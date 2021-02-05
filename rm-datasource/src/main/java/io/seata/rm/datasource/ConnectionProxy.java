/*
 *  Copyright 1999-2019 Seata.io Group.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.seata.rm.datasource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Callable;

import io.seata.common.util.StringUtils;
import io.seata.config.ConfigurationFactory;
import io.seata.core.constants.ConfigurationKeys;
import io.seata.core.exception.TransactionException;
import io.seata.core.exception.TransactionExceptionCode;
import io.seata.core.model.BranchStatus;
import io.seata.core.model.BranchType;
import io.seata.rm.DefaultResourceManager;
import io.seata.rm.datasource.exec.LockConflictException;
import io.seata.rm.datasource.exec.LockRetryController;
import io.seata.rm.datasource.undo.SQLUndoLog;
import io.seata.rm.datasource.undo.UndoLogManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.seata.common.DefaultValues.DEFAULT_CLIENT_LOCK_RETRY_POLICY_BRANCH_ROLLBACK_ON_CONFLICT;
import static io.seata.common.DefaultValues.DEFAULT_CLIENT_REPORT_RETRY_COUNT;
import static io.seata.common.DefaultValues.DEFAULT_CLIENT_REPORT_SUCCESS_ENABLE;

/**
 * The type Connection proxy.
 * 数据库连接代理
 * @author sharajava
 */
public class ConnectionProxy extends AbstractConnectionProxy {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionProxy.class);

    private ConnectionContext context = new ConnectionContext();

    /**
     * 报告尝试次数
     */
    private static final int REPORT_RETRY_COUNT = ConfigurationFactory.getInstance().getInt(
        ConfigurationKeys.CLIENT_REPORT_RETRY_COUNT, DEFAULT_CLIENT_REPORT_RETRY_COUNT);

    /**
     * 是否报告成功状态
     */
    public static final boolean IS_REPORT_SUCCESS_ENABLE = ConfigurationFactory.getInstance().getBoolean(
        ConfigurationKeys.CLIENT_REPORT_SUCCESS_ENABLE, DEFAULT_CLIENT_REPORT_SUCCESS_ENABLE);

    private final static LockRetryPolicy LOCK_RETRY_POLICY = new LockRetryPolicy();

    /**
     * Instantiates a new Connection proxy.
     *
     * @param dataSourceProxy  the data source proxy
     * @param targetConnection the target connection
     */
    public ConnectionProxy(DataSourceProxy dataSourceProxy, Connection targetConnection) {
        super(dataSourceProxy, targetConnection);
    }

    /**
     * Gets context.
     *
     * @return the context
     */
    public ConnectionContext getContext() {
        return context;
    }

    /**
     * Bind.
     * 绑定全局事务Xid
     *
     * @param xid the xid
     */
    public void bind(String xid) {
        context.bind(xid);
    }

    /**
     * set global lock requires flag
     * 是否需要获取全局锁
     * @param isLock whether to lock
     */
    public void setGlobalLockRequire(boolean isLock) {
        context.setGlobalLockRequire(isLock);
    }

    /**
     * get global lock requires flag
     */
    public boolean isGlobalLockRequire() {
        return context.isGlobalLockRequire();
    }

    /**
     * Check lock.
     * 检查全局锁
     * @param lockKeys the lockKeys
     * @throws SQLException the sql exception
     */
    public void checkLock(String lockKeys) throws SQLException {
        if (StringUtils.isBlank(lockKeys)) {
            return;
        }
        // Just check lock without requiring lock by now.
        try {
            boolean lockable = DefaultResourceManager.get().lockQuery(BranchType.AT,
                getDataSourceProxy().getResourceId(), context.getXid(), lockKeys);
            if (!lockable) {
                throw new LockConflictException();
            }
        } catch (TransactionException e) {
            recognizeLockKeyConflictException(e, lockKeys);
        }
    }

    /**
     * Lock query.
     * 全局锁查询
     * @param lockKeys the lock keys
     * @throws SQLException the sql exception
     */
    public boolean lockQuery(String lockKeys) throws SQLException {
        // Just check lock without requiring lock by now.
        boolean result = false;
        try {
            result = DefaultResourceManager.get().lockQuery(BranchType.AT, getDataSourceProxy().getResourceId(),
                context.getXid(), lockKeys);
        } catch (TransactionException e) {
            recognizeLockKeyConflictException(e, lockKeys);
        }
        return result;
    }

    private void recognizeLockKeyConflictException(TransactionException te) throws SQLException {
        recognizeLockKeyConflictException(te, null);
    }

    /**
     * @param te
     * @param lockKeys
     * @throws SQLException
     */
    private void recognizeLockKeyConflictException(TransactionException te, String lockKeys) throws SQLException {
        if (te.getCode() == TransactionExceptionCode.LockKeyConflict) {
            //锁冲突
            StringBuilder reasonBuilder = new StringBuilder("get global lock fail, xid:");
            reasonBuilder.append(context.getXid());
            if (StringUtils.isNotBlank(lockKeys)) {
                reasonBuilder.append(", lockKeys:").append(lockKeys);
            }
            throw new LockConflictException(reasonBuilder.toString());
        } else {
            throw new SQLException(te);
        }

    }

    /**
     * append sqlUndoLog
     *
     * @param sqlUndoLog the sql undo log
     */
    public void appendUndoLog(SQLUndoLog sqlUndoLog) {
        context.appendUndoItem(sqlUndoLog);
    }

    /**
     * append lockKey
     *
     * @param lockKey the lock key
     */
    public void appendLockKey(String lockKey) {
        context.appendLockKey(lockKey);
    }

    /**
     * 提交事务分支
     * @throws SQLException
     */
    @Override
    public void commit() throws SQLException {
        try {
            LOCK_RETRY_POLICY.execute(() -> {
                doCommit();
                return null;
            });
        } catch (SQLException e) {
            if (targetConnection != null && !getAutoCommit()) {
                rollback();
            }
            throw e;
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    /**
     * @throws SQLException
     */
    private void doCommit() throws SQLException {
        if (context.inGlobalTransaction()) {
            //在全局事务中提交分支事务
            processGlobalTransactionCommit();
        } else if (context.isGlobalLockRequire()) {
            //一般为TM，事务发起方，先获取全局锁
            processLocalCommitWithGlobalLocks();
        } else {
            //直接提交
            targetConnection.commit();
        }
    }

    /**
     * 在全局锁的情况下执行提交
     * @throws SQLException
     */
    private void processLocalCommitWithGlobalLocks() throws SQLException {
        checkLock(context.buildLockKeys());
        try {
            targetConnection.commit();
        } catch (Throwable ex) {
            throw new SQLException(ex);
        }
        context.reset();
    }

    /**
     * 处理全局事务提交
     * 1. 注册事务分支
     * 2. 刷盘分支事务undo log（落库，持久化）
     * 3. 提交SQL
     * 4. 报告事务分支状态（BranchStatus.PhaseOne_Done， BranchStatus.PhaseOne_Failed）
     * @throws SQLException
     */
    private void processGlobalTransactionCommit() throws SQLException {
        try {
            //注册事务分支
            register();
        } catch (TransactionException e) {
            recognizeLockKeyConflictException(e, context.buildLockKeys());
        }
        try {
            //刷盘分支事务undo log（落库，持久化）
            UndoLogManagerFactory.getUndoLogManager(this.getDbType()).flushUndoLogs(this);
            //提交SQL
            targetConnection.commit();
        } catch (Throwable ex) {
            LOGGER.error("process connectionProxy commit error: {}", ex.getMessage(), ex);
            report(false);
            throw new SQLException(ex);
        }
        if (IS_REPORT_SUCCESS_ENABLE) {
            //报告事务分支状态
            report(true);
        }
        //清除连接上下文
        context.reset();
    }

    /**
     * 注册
     * @throws TransactionException
     */
    private void register() throws TransactionException {
        if (!context.hasUndoLog() || context.getLockKeysBuffer().isEmpty()) {
            return;
        }
        Long branchId = DefaultResourceManager.get().branchRegister(BranchType.AT, getDataSourceProxy().getResourceId(),
            null, context.getXid(), null, context.buildLockKeys());
        context.setBranchId(branchId);
    }

    /**
     * 回滚
     * @throws SQLException
     */
    @Override
    public void rollback() throws SQLException {
        //回滚当前事物
        targetConnection.rollback();
        if (context.inGlobalTransaction() && context.isBranchRegistered()) {
            report(false);
        }
        context.reset();
    }

    /**
     * 自动提交
     * @param autoCommit
     * @throws SQLException
     */
    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        if ((context.inGlobalTransaction() || context.isGlobalLockRequire()) && autoCommit && !getAutoCommit()) {
            // change autocommit from false to true, we should commit() first according to JDBC spec.
            // 处在全局事务下
            doCommit();
        }
        //自动提交
        targetConnection.setAutoCommit(autoCommit);
    }

    /**
     * 报告事务状态
     * @param commitDone
     * @throws SQLException
     */
    private void report(boolean commitDone) throws SQLException {
        if (context.getBranchId() == null) {
            return;
        }
        int retry = REPORT_RETRY_COUNT;
        while (retry > 0) {
            try {
                DefaultResourceManager.get().branchReport(BranchType.AT, context.getXid(), context.getBranchId(),
                    commitDone ? BranchStatus.PhaseOne_Done : BranchStatus.PhaseOne_Failed, null);
                return;
            } catch (Throwable ex) {
                LOGGER.error("Failed to report [" + context.getBranchId() + "/" + context.getXid() + "] commit done ["
                    + commitDone + "] Retry Countdown: " + retry);
                retry--;

                if (retry == 0) {
                    throw new SQLException("Failed to report branch status " + commitDone, ex);
                }
            }
        }
    }

    public static class LockRetryPolicy {
        protected static final boolean LOCK_RETRY_POLICY_BRANCH_ROLLBACK_ON_CONFLICT = ConfigurationFactory
            .getInstance().getBoolean(ConfigurationKeys.CLIENT_LOCK_RETRY_POLICY_BRANCH_ROLLBACK_ON_CONFLICT, DEFAULT_CLIENT_LOCK_RETRY_POLICY_BRANCH_ROLLBACK_ON_CONFLICT);

        public <T> T execute(Callable<T> callable) throws Exception {
            if (LOCK_RETRY_POLICY_BRANCH_ROLLBACK_ON_CONFLICT) {
                return callable.call();
            } else {
                return doRetryOnLockConflict(callable);
            }
        }

        protected <T> T doRetryOnLockConflict(Callable<T> callable) throws Exception {
            LockRetryController lockRetryController = new LockRetryController();
            while (true) {
                try {
                    return callable.call();
                } catch (LockConflictException lockConflict) {
                    onException(lockConflict);
                    lockRetryController.sleep(lockConflict);
                } catch (Exception e) {
                    onException(e);
                    throw e;
                }
            }
        }

        /**
         * Callback on exception in doLockRetryOnConflict.
         *
         * @param e invocation exception
         * @throws Exception error
         */
        protected void onException(Exception e) throws Exception {
        }
    }
}
