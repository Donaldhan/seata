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
package io.seata.core.protocol.transaction;

import io.seata.core.rpc.RpcContext;

/**
 * The interface Tc inbound handler.
 * TC 请求处理器
 *
 * @author sharajava
 */
public interface TCInboundHandler {

    /**
     * Handle global begin response.
     * 处理全局事务开始
     * @param globalBegin the global begin
     * @param rpcContext  the rpc context
     * @return the global begin response
     */
    GlobalBeginResponse handle(GlobalBeginRequest globalBegin, RpcContext rpcContext);

    /**
     * Handle global commit response.
     * 处理全局事务提交
     * @param globalCommit the global commit
     * @param rpcContext   the rpc context
     * @return the global commit response
     */
    GlobalCommitResponse handle(GlobalCommitRequest globalCommit, RpcContext rpcContext);

    /**
     * Handle global rollback response.
     * 处理全局事务回滚
     * @param globalRollback the global rollback
     * @param rpcContext     the rpc context
     * @return the global rollback response
     */
    GlobalRollbackResponse handle(GlobalRollbackRequest globalRollback, RpcContext rpcContext);

    /**
     * Handle branch register response.
     *  全局事务注册
     * @param branchRegister the branch register
     * @param rpcContext     the rpc context
     * @return the branch register response
     */
    BranchRegisterResponse handle(BranchRegisterRequest branchRegister, RpcContext rpcContext);

    /**
     * Handle branch report response.
     * 分支report
     * @param branchReport the branch report
     * @param rpcContext   the rpc context
     * @return the branch report response
     */
    BranchReportResponse handle(BranchReportRequest branchReport, RpcContext rpcContext);

    /**
     * Handle global lock query response.
     * 全局事务锁查询
     * @param checkLock  the check lock
     * @param rpcContext the rpc context
     * @return the global lock query response
     */
    GlobalLockQueryResponse handle(GlobalLockQueryRequest checkLock, RpcContext rpcContext);

    /**
     * Handle global status response.
     * 全局事务状态
     * @param globalStatus the global status
     * @param rpcContext   the rpc context
     * @return the global status response
     */
    GlobalStatusResponse handle(GlobalStatusRequest globalStatus, RpcContext rpcContext);

    /**
     * Handle global report request.
     * 全局事务report
     * @param globalReport the global report request
     * @param rpcContext   the rpc context
     * @return the global report response
     */
    GlobalReportResponse handle(GlobalReportRequest globalReport, RpcContext rpcContext);

}
