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
package io.seata.integration.dubbo;

import io.seata.common.util.StringUtils;
import io.seata.core.constants.DubboConstants;
import io.seata.core.context.RootContext;
import io.seata.core.model.BranchType;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The type Transaction propagation filter.
 * 基于DUBBO的，事务传播过滤器
 *
 * 基于DUBBO的AT服务，主要根据RootContext的XID和事务分支类型，决定是否处于全局事务中，及执行的事务类型
 * @author sharajava
 */
@Activate(group = {DubboConstants.PROVIDER, DubboConstants.CONSUMER}, order = 100)
public class ApacheDubboTransactionPropagationFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApacheDubboTransactionPropagationFilter.class);

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        String xid = RootContext.getXID();
        //默认为AT模式
        BranchType branchType = RootContext.getBranchType();
        //获取rpc全局事务id
        String rpcXid = getRpcXid();
        String rpcBranchType = RpcContext.getContext().getAttachment(RootContext.KEY_BRANCH_TYPE);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("xid in RootContext[{}] xid in RpcContext[{}]", xid, rpcXid);
        }
        boolean bind = false;
        if (xid != null) {
            //Dubbo服务消费者 Seata AT 事务分支
            //将全局事务的xid和分支类型，放到DUBBO RpcContext的Attachment中
            RpcContext.getContext().setAttachment(RootContext.KEY_XID, xid);
            RpcContext.getContext().setAttachment(RootContext.KEY_BRANCH_TYPE, branchType.name());
        } else {
            if (rpcXid != null) {
                //Dubbo服务提供者 Seata AT 事务分支
                RootContext.bind(rpcXid);
                if (StringUtils.equals(BranchType.TCC.name(), rpcBranchType)) {
                    RootContext.bindBranchType(BranchType.TCC);
                }
                bind = true;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("bind xid [{}] branchType [{}] to RootContext", rpcXid, rpcBranchType);
                }
            }
        }
        try {
            return invoker.invoke(invocation);
        } finally {
            if (bind) {
                //解绑全局事务（RootContext），Xid及事务分支类型
                BranchType previousBranchType = RootContext.getBranchType();
                String unbindXid = RootContext.unbind();
                if (BranchType.TCC == previousBranchType) {
                    RootContext.unbindBranchType();
                }
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("unbind xid [{}] branchType [{}] from RootContext", unbindXid, previousBranchType);
                }
                if (!rpcXid.equalsIgnoreCase(unbindXid)) {
                    LOGGER.warn("xid in change during RPC from {} to {},branchType from {} to {}", rpcXid, unbindXid,
                            rpcBranchType != null ? rpcBranchType : "AT", previousBranchType);
                    if (unbindXid != null) {
                        RootContext.bind(unbindXid);
                        LOGGER.warn("bind xid [{}] back to RootContext", unbindXid);
                        if (BranchType.TCC == previousBranchType) {
                            RootContext.bindBranchType(BranchType.TCC);
                            LOGGER.warn("bind branchType [{}] back to RootContext", previousBranchType);
                        }
                    }
                }
            }
        }
    }

    /**
     * 获取rpc全局事务id
     * get rpc xid
     * @return
     */
    private String getRpcXid() {
        String rpcXid = RpcContext.getContext().getAttachment(RootContext.KEY_XID);
        if (rpcXid == null) {
            rpcXid = RpcContext.getContext().getAttachment(RootContext.KEY_XID.toLowerCase());
        }
        return rpcXid;
    }

}
