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
package io.seata.core.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Status of global transaction.
 * 全局事务状态
 * @author sharajava
 */
public enum GlobalStatus {

    /**
     * Un known global status. 未知状态
     */
    // Unknown
    UnKnown(0),

    /**
     * The Begin. 全局事务开始，接收新的分支注册
     * // PHASE 1: can accept new branch registering.
     */

    Begin(1),

    /**
     * PHASE 2: Running Status: may be changed any time.
     * // Committing.
     * 二阶段运行状态，可以能在任何时候改变， 提交中
     */

    Committing(2),

    /**
     * The Commit retrying.
     * // Retrying commit after a recoverable failure.
     * 在失败回复轨后，尝试提交
     */

    CommitRetrying(3),

    /**
     * Rollbacking global status.
     *  // Rollbacking
     *  回滚全局状态
     */
    Rollbacking(4),

    /**
     * The Rollback retrying.
     * // Retrying rollback after a recoverable failure.
     * 在失败恢复后的，回滚尝试
     */
    RollbackRetrying(5),
    /**
     * The Timeout rollbacking.
     * // Rollbacking since timeout
     * 回滚超时
     */
    TimeoutRollbacking(6),
    /**
     * The Timeout rollback retrying.
     * // Retrying rollback (since timeout) after a recoverable failure.
     * 由于尝试失败的回滚尝试
     */
    TimeoutRollbackRetrying(7),

    /**
     * All branches can be async committed. The committing is NOT done yet, but it can be seen as committed for TM/RM
     * client.
     * 所有的分支可以异步提交。这个提交状态还没有完成，此状态TM和RM可以看到
     */
    AsyncCommitting(8),

    /**
     * PHASE 2: Final Status: will NOT change any more.
     *  // Finally: global transaction is successfully committed.
     *  事务的最终成功状态，Committed
     */
    Committed(9),

    /**
     * The Commit failed.
     * // Finally: failed to commit
     * 最终状态，提交失败
     */
    CommitFailed(10),

    /**
     * The Rollbacked.
     *  // Finally: global transaction is successfully rollbacked.
     *  最终状态，回滚
     */
    Rollbacked(11),

    /**
     * The Rollback failed.
     *   // Finally: failed to rollback
     *   最终状态，回滚失败
     */
    RollbackFailed(12),
    /**
     * The Timeout rollbacked.
     *  // Finally: global transaction is successfully rollbacked since timeout.
     *  最终状态：由于超时，全局事务回滚失败
     */
    TimeoutRollbacked(13),
    /**
     * The Timeout rollback failed.
     *  最终状态, 超时失败
     *   // Finally: failed to rollback since timeout
     */
    TimeoutRollbackFailed(14),

    /**
     * The Finished.
     *  最终状态：
     * // Not managed in session MAP any more
     */
    Finished(15);

    private int code;

    GlobalStatus(int code) {
        this.code = code;
    }


    /**
     * Gets code.
     *
     * @return the code
     */
    public int getCode() {
        return code;
    }



    private static final Map<Integer, GlobalStatus> MAP = new HashMap<>(values().length);

    static {
        for (GlobalStatus status : values()) {
            MAP.put(status.code, status);
        }
    }

    /**
     * Get global status.
     *
     * @param code the code
     * @return the global status
     */
    public static GlobalStatus get(byte code) {
        return get((int)code);
    }

    /**
     * Get global status.
     *
     * @param code the code
     * @return the global status
     */
    public static GlobalStatus get(int code) {
        GlobalStatus status = MAP.get(code);

        if (status == null) {
            throw new IllegalArgumentException("Unknown GlobalStatus[" + code + "]");
        }

        return status;
    }
}
