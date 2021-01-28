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
package io.seata.core.rpc.processor.client;

import io.netty.channel.ChannelHandlerContext;
import io.seata.core.protocol.AbstractResultMessage;
import io.seata.core.protocol.MergeMessage;
import io.seata.core.protocol.MergeResultMessage;
import io.seata.core.protocol.MergedWarpMessage;
import io.seata.core.protocol.MessageFuture;
import io.seata.core.protocol.RegisterRMResponse;
import io.seata.core.protocol.RegisterTMResponse;
import io.seata.core.protocol.RpcMessage;
import io.seata.core.protocol.transaction.BranchRegisterResponse;
import io.seata.core.protocol.transaction.BranchReportResponse;
import io.seata.core.protocol.transaction.GlobalBeginResponse;
import io.seata.core.protocol.transaction.GlobalCommitResponse;
import io.seata.core.protocol.transaction.GlobalLockQueryResponse;
import io.seata.core.protocol.transaction.GlobalReportResponse;
import io.seata.core.protocol.transaction.GlobalRollbackResponse;
import io.seata.core.rpc.TransactionMessageHandler;
import io.seata.core.rpc.processor.RemotingProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * process TC response message.
 * 处理TC响应消息
 * <p>
 * process message type:
 * RM:
 * 1) {@link MergeResultMessage}
 * 2) {@link RegisterRMResponse} 注册RM响应
 * 3) {@link BranchRegisterResponse} 分支注册响应
 * 4) {@link BranchReportResponse}
 * 5) {@link GlobalLockQueryResponse} 全局锁查询响应
 * TM:
 * 1) {@link MergeResultMessage}
 * 2) {@link RegisterTMResponse} 注册TM响应
 * 3) {@link GlobalBeginResponse} 全局事务开始响应
 * 4) {@link GlobalCommitResponse} 全局事务提交响应
 * 5) {@link GlobalReportResponse}
 * 6) {@link GlobalRollbackResponse} 全局事务回滚
 *
 * @author zhangchenghui.dev@gmail.com
 * @since 1.3.0
 */
public class ClientOnResponseProcessor implements RemotingProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientOnResponseProcessor.class);

    /**
     * The Merge msg map from
     * @see io.seata.core.rpc.netty.AbstractNettyRemotingClient#mergeMsgMap
     */
    private Map<Integer, MergeMessage> mergeMsgMap;

    /**
     * The Futures from
     * @see io.seata.core.rpc.netty.AbstractNettyRemoting#futures
     */
    private ConcurrentMap<Integer, MessageFuture> futures;

    /**
     * To handle the received RPC message on upper level.
     */
    private TransactionMessageHandler transactionMessageHandler;

    public ClientOnResponseProcessor(Map<Integer, MergeMessage> mergeMsgMap,
                                     ConcurrentHashMap<Integer, MessageFuture> futures,
                                     TransactionMessageHandler transactionMessageHandler) {
        this.mergeMsgMap = mergeMsgMap;
        this.futures = futures;
        this.transactionMessageHandler = transactionMessageHandler;
    }

    /**
     * 如果为RmNettyRemotingClient， transactionMessageHandler>DefaultRMHandler
     * 非异步操作，委托给事务处理器
     * @param ctx        Channel handler context.
     * @param rpcMessage rpc message.
     * @throws Exception
     */
    @Override
    public void process(ChannelHandlerContext ctx, RpcMessage rpcMessage) throws Exception {
        if (rpcMessage.getBody() instanceof MergeResultMessage) {
            //合并结果
            MergeResultMessage results = (MergeResultMessage) rpcMessage.getBody();
            MergedWarpMessage mergeMessage = (MergedWarpMessage) mergeMsgMap.remove(rpcMessage.getId());
            for (int i = 0; i < mergeMessage.msgs.size(); i++) {
                int msgId = mergeMessage.msgIds.get(i);
                MessageFuture future = futures.remove(msgId);
                if (future == null) {
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("msg: {} is not found in futures.", msgId);
                    }
                } else {
                    //设置MessageFuture的相应结果
                    future.setResultMessage(results.getMsgs()[i]);
                }
            }
        } else {
            MessageFuture messageFuture = futures.remove(rpcMessage.getId());
            if (messageFuture != null) {
                //设置MessageFuture的相应结果
                messageFuture.setResultMessage(rpcMessage.getBody());
            } else {
                if (rpcMessage.getBody() instanceof AbstractResultMessage) {
                    if (transactionMessageHandler != null) {
                        //委托给事务处理器
                        transactionMessageHandler.onResponse((AbstractResultMessage) rpcMessage.getBody(), null);
                    }
                }
            }
        }
    }
}
