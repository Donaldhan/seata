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
package io.seata.server.coordinator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.seata.server.storage.db.session.DataBaseSessionManager;
import io.seata.server.storage.db.store.DataBaseTransactionStoreManager;
import io.seata.server.transaction.at.ATCore;
import io.seata.server.transaction.saga.SagaCore;
import io.seata.server.transaction.tcc.TccCore;
import io.seata.server.transaction.xa.XACore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.seata.common.exception.NotSupportYetException;
import io.seata.common.loader.EnhancedServiceLoader;
import io.seata.common.util.CollectionUtils;
import io.seata.core.event.EventBus;
import io.seata.core.event.GlobalTransactionEvent;
import io.seata.core.exception.TransactionException;
import io.seata.core.logger.StackTraceLogger;
import io.seata.core.model.BranchStatus;
import io.seata.core.model.BranchType;
import io.seata.core.model.GlobalStatus;
import io.seata.core.rpc.RemotingServer;
import io.seata.server.event.EventBusManager;
import io.seata.server.session.BranchSession;
import io.seata.server.session.GlobalSession;
import io.seata.server.session.SessionHelper;
import io.seata.server.session.SessionHolder;

/**
 * The type Default core.
 * core事务处理
 * @author sharajava
 */
public class DefaultCore implements Core {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultCore.class);

    private EventBus eventBus = EventBusManager.get();

    /**
     *
     */
    private static Map<BranchType, AbstractCore> coreMap = new ConcurrentHashMap<>();

    /**
     * get the Default core.
     * {@link ATCore}
     * {@link XACore}
     * {@link TccCore}
     * {@link SagaCore}
     * @param remotingServer the remoting server
     */
    public DefaultCore(RemotingServer remotingServer) {
        List<AbstractCore> allCore = EnhancedServiceLoader.loadAll(AbstractCore.class,
            new Class[]{RemotingServer.class}, new Object[]{remotingServer});
        if (CollectionUtils.isNotEmpty(allCore)) {
            for (AbstractCore core : allCore) {
                coreMap.put(core.getHandleBranchType(), core);
            }
        }
    }

    /**
     * get core
     *
     * @param branchType the branchType
     * @return the core
     */
    public AbstractCore getCore(BranchType branchType) {
        AbstractCore core = coreMap.get(branchType);
        if (core == null) {
            throw new NotSupportYetException("unsupported type:" + branchType.name());
        }
        return core;
    }

    /**
     * only for mock
     *
     * @param branchType the branchType
     * @param core       the core
     */
    public void mockCore(BranchType branchType, AbstractCore core) {
        coreMap.put(branchType, core);
    }

    /**
     * 注册分支事务
     *
     * @param branchType      the branch type
     * @param resourceId      the resource id
     * @param clientId        the client id
     * @param xid             the xid
     * @param applicationData the context
     * @param lockKeys        the lock keys
     * @return
     * @throws TransactionException
     */
    @Override
    public Long branchRegister(BranchType branchType, String resourceId, String clientId, String xid,
                               String applicationData, String lockKeys) throws TransactionException {
        return getCore(branchType).branchRegister(branchType, resourceId, clientId, xid,
            applicationData, lockKeys);
    }

    /**
     * @param branchType      the branch type
     * @param xid             the xid
     * @param branchId        the branch id
     * @param status          the status
     * @param applicationData the application data
     * @throws TransactionException
     */
    @Override
    public void branchReport(BranchType branchType, String xid, long branchId, BranchStatus status,
                             String applicationData) throws TransactionException {
        getCore(branchType).branchReport(branchType, xid, branchId, status, applicationData);
    }

    @Override
    public boolean lockQuery(BranchType branchType, String resourceId, String xid, String lockKeys)
        throws TransactionException {
        return getCore(branchType).lockQuery(branchType, resourceId, xid, lockKeys);
    }

    @Override
    public BranchStatus branchCommit(GlobalSession globalSession, BranchSession branchSession) throws TransactionException {
        return getCore(branchSession.getBranchType()).branchCommit(globalSession, branchSession);
    }

    /**
     * 发送分支事务回滚消息
     * @param globalSession the global session
     * @param branchSession the branch session
     * @return
     * @throws TransactionException
     */
    @Override
    public BranchStatus branchRollback(GlobalSession globalSession, BranchSession branchSession) throws TransactionException {
        return getCore(branchSession.getBranchType()).branchRollback(globalSession, branchSession);
    }

    /**
     * 触发全局事务开始事件， 全局会话监听器DataBaseSessionManager处理相应事件
     * {@link DataBaseSessionManager#addGlobalSession(io.seata.server.session.GlobalSession)}
     * @param applicationId           ID of the application who begins this transaction.
     * @param transactionServiceGroup ID of the transaction service group.
     * @param name                    Give a name to the global transaction.
     * @param timeout                 Timeout of the global transaction.
     * @return
     * @throws TransactionException
     */
    @Override
    public String begin(String applicationId, String transactionServiceGroup, String name, int timeout)
        throws TransactionException {
        GlobalSession session = GlobalSession.createGlobalSession(applicationId, transactionServiceGroup, name,
            timeout);
        session.addSessionLifecycleListener(SessionHolder.getRootSessionManager());

        session.begin();

        // transaction start event
        eventBus.post(new GlobalTransactionEvent(session.getTransactionId(), GlobalTransactionEvent.ROLE_TC,
            session.getTransactionName(), session.getBeginTime(), null, session.getStatus()));

        return session.getXid();
    }

    /**
     * 触发全局事务提交事件， 全局会话监听器DataBaseSessionManager处理相应事件
     * {@link DataBaseTransactionStoreManager#writeSession(io.seata.server.store.TransactionStoreManager.LogOperation, io.seata.server.store.SessionStorable)}
     * 委托{@link GlobalSession#changeStatus(GlobalStatus)}
     * @param xid XID of the global transaction.
     * @return
     * @throws TransactionException
     */
    @Override
    public GlobalStatus commit(String xid) throws TransactionException {
        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);
        if (globalSession == null) {
            return GlobalStatus.Finished;
        }
        globalSession.addSessionLifecycleListener(SessionHolder.getRootSessionManager());
        // just lock changeStatus

        boolean shouldCommit = SessionHolder.lockAndExecute(globalSession, () -> {
            // Highlight: Firstly, close the session, then no more branch can be registered.
            //disable 全局会话状态， 释放全局事务的分支相关行锁, 不在接收分支注册
            globalSession.closeAndClean();
            if (globalSession.getStatus() == GlobalStatus.Begin) {
                if (globalSession.canBeCommittedAsync()) {
                    globalSession.asyncCommit();
                    return false;
                } else {
                    //分支可以提交
                    globalSession.changeStatus(GlobalStatus.Committing);
                    return true;
                }
            }
            return false;
        });

        if (shouldCommit) {
            //如果可以提交，则尝试全局提交
            boolean success = doGlobalCommit(globalSession, false);
            //If successful and all remaining branches can be committed asynchronously, do async commit.
            //如果全局提交成功，且海鸥需要异步提交的分支，做异步提交
            if (success && globalSession.hasBranch() && globalSession.canBeCommittedAsync()) {
                globalSession.asyncCommit();
                return GlobalStatus.Committed;
            } else {
                return globalSession.getStatus();
            }
        } else {
            return globalSession.getStatus() == GlobalStatus.AsyncCommitting ? GlobalStatus.Committed : globalSession.getStatus();
        }
    }

    /**
     *
     * 尝试提交全局事务，首先针对各分支事务，尝试提交，
     * 如果分支两阶段提交成功，移除相关分时会话（释放锁，从事务管理器中移除事务分支，并从全局事务会话中移除分支会话）；
     * 待完成检查分支的提交状态后，检查全局会话是否还有未提交的分支，如果全局可以异步提交，则全局事务提交成功，否则提交失败；
     * 全局事务提交成功则结束全局事务(更新全局事务状态为已提交{@link GlobalStatus#Committed}，释放全局事务相关的锁，并从会话管理器中移除全局会话)
     *
     * @param globalSession the global session
     * @param retrying      the retrying
     * @return
     * @throws TransactionException
     */
    @Override
    public boolean doGlobalCommit(GlobalSession globalSession, boolean retrying) throws TransactionException {
        boolean success = true;
        // start committing event 开启提交事务
        eventBus.post(new GlobalTransactionEvent(globalSession.getTransactionId(), GlobalTransactionEvent.ROLE_TC,
            globalSession.getTransactionName(), globalSession.getBeginTime(), null, globalSession.getStatus()));

        if (globalSession.isSaga()) {
            success = getCore(BranchType.SAGA).doGlobalCommit(globalSession, retrying);
        } else {
            for (BranchSession branchSession : globalSession.getSortedBranches()) {
                // if not retrying, skip the canBeCommittedAsync branches
                // 如果不需要重试，则跳过可以异步提交分分支
                if (!retrying && branchSession.canBeCommittedAsync()) {
                    continue;
                }

                BranchStatus currentStatus = branchSession.getStatus();
                if (currentStatus == BranchStatus.PhaseOne_Failed) {
                    //移除一阶段失败的分支会话
                    globalSession.removeBranch(branchSession);
                    continue;
                }
                try {
                    //发送分支事务提交消息
                    BranchStatus branchStatus = getCore(branchSession.getBranchType()).branchCommit(globalSession, branchSession);

                    switch (branchStatus) {
                        case PhaseTwo_Committed:
                            //两阶段提交完成移除分支
                            globalSession.removeBranch(branchSession);
                            continue;
                        case PhaseTwo_CommitFailed_Unretryable:
                            //两阶段提交失败，不可以重试
                            if (globalSession.canBeCommittedAsync()) {
                                LOGGER.error(
                                    "Committing branch transaction[{}], status: PhaseTwo_CommitFailed_Unretryable, please check the business log.", branchSession.getBranchId());
                                continue;
                            } else {
                                SessionHelper.endCommitFailed(globalSession);
                                LOGGER.error("Committing global transaction[{}] finally failed, caused by branch transaction[{}] commit failed.", globalSession.getXid(), branchSession.getBranchId());
                                return false;
                            }
                        default:
                            if (!retrying) {
                                // 添加全局会话到重试提交管理器，并更新全局事务状态为{@link GlobalStatus#CommitRetrying}
                                globalSession.queueToRetryCommit();
                                return false;
                            }
                            if (globalSession.canBeCommittedAsync()) {
                                LOGGER.error("Committing branch transaction[{}], status:{} and will retry later",
                                    branchSession.getBranchId(), branchStatus);
                                continue;
                            } else {
                                LOGGER.error(
                                    "Committing global transaction[{}] failed, caused by branch transaction[{}] commit failed, will retry later.", globalSession.getXid(), branchSession.getBranchId());
                                return false;
                            }
                    }
                } catch (Exception ex) {
                    StackTraceLogger.error(LOGGER, ex, "Committing branch transaction exception: {}",
                        new String[] {branchSession.toString()});
                    if (!retrying) {
                        globalSession.queueToRetryCommit();
                        throw new TransactionException(ex);
                    }
                }
            }
            //If has branch and not all remaining branches can be committed asynchronously,
            //do print log and return false
            // 如果还有未提交的分支，并且这些分支不可以异步提交, 则提交失败
            if (globalSession.hasBranch() && !globalSession.canBeCommittedAsync()) {
                LOGGER.info("Committing global transaction is NOT done, xid = {}.", globalSession.getXid());
                return false;
            }
        }
        //If success and there is no branch, end the global transaction.
        //结束全局事务
        if (success && globalSession.getBranchSessions().isEmpty()) {
            //更新全局事务状态为已提交{@link GlobalStatus#Committed}，释放全局事务相关的锁，并从会话管理器中移除全局会话
            SessionHelper.endCommitted(globalSession);

            // committed event
            eventBus.post(new GlobalTransactionEvent(globalSession.getTransactionId(), GlobalTransactionEvent.ROLE_TC,
                globalSession.getTransactionName(), globalSession.getBeginTime(), System.currentTimeMillis(),
                globalSession.getStatus()));

            LOGGER.info("Committing global transaction is successfully done, xid = {}.", globalSession.getXid());
        }
        return success;
    }

    /**
     * @param xid XID of the global transaction
     * @return
     * @throws TransactionException
     */
    @Override
    public GlobalStatus rollback(String xid) throws TransactionException {
        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);
        if (globalSession == null) {
            return GlobalStatus.Finished;
        }
        globalSession.addSessionLifecycleListener(SessionHolder.getRootSessionManager());
        // just lock changeStatus
        boolean shouldRollBack = SessionHolder.lockAndExecute(globalSession, () -> {
            //disable session
            globalSession.close();
            // Highlight: Firstly, close the session, then no more branch can be registered.
            if (globalSession.getStatus() == GlobalStatus.Begin) {
                //开始状态可以回滚
                globalSession.changeStatus(GlobalStatus.Rollbacking);
                return true;
            }
            return false;
        });
        if (!shouldRollBack) {
            //不可回滚，直接返回当前全局事务状态
            return globalSession.getStatus();
        }

        doGlobalRollback(globalSession, false);
        return globalSession.getStatus();
    }

    /**
     * 针对非SAGA模式事务：
     * 遍历全局事务的分支事务会话，如果事务会话一阶段失败，移除分支会话；
     * 否则发送回滚分支事务请求给RM分支，RM回滚本地分支事务；如果分支事务回滚成功，则移除分支事务；
     * 如果分支事务回滚失败，不可尝试，结束全局事务，回滚失败（释放全局事务相关的锁，并从会话管理器中移除全局会话）；
     * 如果所有分支事务全部回滚完，则结束全部事务（回滚成功Rollbacked，或回滚超时TimeoutRollbacked，释放全局事务相关的锁，并从会话管理器中移除全局会话）
     *
     * @param globalSession the global session
     * @param retrying      the retrying
     * @return
     * @throws TransactionException
     */
    @Override
    public boolean doGlobalRollback(GlobalSession globalSession, boolean retrying) throws TransactionException {
        boolean success = true;
        // start rollback event 正在回滚事务
        eventBus.post(new GlobalTransactionEvent(globalSession.getTransactionId(), GlobalTransactionEvent.ROLE_TC,
            globalSession.getTransactionName(), globalSession.getBeginTime(), null, globalSession.getStatus()));

        if (globalSession.isSaga()) {
            success = getCore(BranchType.SAGA).doGlobalRollback(globalSession, retrying);
        } else {
            for (BranchSession branchSession : globalSession.getReverseSortedBranches()) {
                BranchStatus currentBranchStatus = branchSession.getStatus();
                if (currentBranchStatus == BranchStatus.PhaseOne_Failed) {
                    //一阶段失败，移除分支会话
                    globalSession.removeBranch(branchSession);
                    continue;
                }
                try {
                    //发送分支事务回滚消息
                    BranchStatus branchStatus = branchRollback(globalSession, branchSession);
                    switch (branchStatus) {
                        case PhaseTwo_Rollbacked:
                            //两阶段已回滚，移除分支
                            globalSession.removeBranch(branchSession);
                            LOGGER.info("Rollback branch transaction successfully, xid = {} branchId = {}", globalSession.getXid(), branchSession.getBranchId());
                            continue;
                        case PhaseTwo_RollbackFailed_Unretryable:
                            //回滚失败，不可尝试，结束全局事务，回滚失败
                            SessionHelper.endRollbackFailed(globalSession);
                            LOGGER.info("Rollback branch transaction fail and stop retry, xid = {} branchId = {}", globalSession.getXid(), branchSession.getBranchId());
                            return false;
                        default:
                            LOGGER.info("Rollback branch transaction fail and will retry, xid = {} branchId = {}", globalSession.getXid(), branchSession.getBranchId());
                            if (!retrying) {
                                //添加到全局会话到重试会馆会话管理器，并更新全局事务状态为{@link GlobalStatus#TimeoutRollbackRetrying}或{@link GlobalStatus#RollbackRetrying}
                                globalSession.queueToRetryRollback();
                            }
                            return false;
                    }
                } catch (Exception ex) {
                    StackTraceLogger.error(LOGGER, ex,
                        "Rollback branch transaction exception, xid = {} branchId = {} exception = {}",
                        new String[] {globalSession.getXid(), String.valueOf(branchSession.getBranchId()), ex.getMessage()});
                    if (!retrying) {
                        globalSession.queueToRetryRollback();
                    }
                    throw new TransactionException(ex);
                }
            }

            // In db mode, there is a problem of inconsistent data in multiple copies, resulting in new branch
            // transaction registration when rolling back.
            // 1. New branch transaction and rollback branch transaction have no data association
            // 2. New branch transaction has data association with rollback branch transaction
            // The second query can solve the first problem, and if it is the second problem, it may cause a rollback
            // failure due to data changes.
            // 在db模式喜爱，存在数据不一致的情况，将会导致在回滚过程，存在新的分支注册。
            // 1. 新添加的事务分支和回滚分支没有任何关系
            // 2. 新的事务分支和回滚分支事务存在数据关联
            // 二次check， 可以解决一个问题，如果为第二个问题，由于数据改变会导致回滚失败。
            GlobalSession globalSessionTwice = SessionHolder.findGlobalSession(globalSession.getXid());
            if (globalSessionTwice != null && globalSessionTwice.hasBranch()) {
                LOGGER.info("Rollbacking global transaction is NOT done, xid = {}.", globalSession.getXid());
                return false;
            }
        }
        if (success) {
            //结束全局事务（回滚成功Rollbacked，或回滚超时TimeoutRollbacked），释放全局事务相关的锁，并从会话管理器中移除全局会话
            SessionHelper.endRollbacked(globalSession);

            // rollbacked event 回滚成功事件
            eventBus.post(new GlobalTransactionEvent(globalSession.getTransactionId(), GlobalTransactionEvent.ROLE_TC,
                globalSession.getTransactionName(), globalSession.getBeginTime(), System.currentTimeMillis(),
                globalSession.getStatus()));

            LOGGER.info("Rollback global transaction successfully, xid = {}.", globalSession.getXid());
        }
        return success;
    }

    @Override
    public GlobalStatus getStatus(String xid) throws TransactionException {
        GlobalSession globalSession = SessionHolder.findGlobalSession(xid, false);
        if (globalSession == null) {
            return GlobalStatus.Finished;
        } else {
            return globalSession.getStatus();
        }
    }

    @Override
    public GlobalStatus globalReport(String xid, GlobalStatus globalStatus) throws TransactionException {
        GlobalSession globalSession = SessionHolder.findGlobalSession(xid);
        if (globalSession == null) {
            return globalStatus;
        }
        globalSession.addSessionLifecycleListener(SessionHolder.getRootSessionManager());
        doGlobalReport(globalSession, xid, globalStatus);
        return globalSession.getStatus();
    }

    @Override
    public void doGlobalReport(GlobalSession globalSession, String xid, GlobalStatus globalStatus) throws TransactionException {
        if (globalSession.isSaga()) {
            getCore(BranchType.SAGA).doGlobalReport(globalSession, xid, globalStatus);
        }
    }
}
