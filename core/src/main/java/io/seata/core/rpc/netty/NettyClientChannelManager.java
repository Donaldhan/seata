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
package io.seata.core.rpc.netty;

import io.netty.channel.Channel;
import io.seata.common.exception.FrameworkErrorCode;
import io.seata.common.exception.FrameworkException;
import io.seata.common.util.CollectionUtils;
import io.seata.common.util.NetUtil;
import io.seata.common.util.StringUtils;
import io.seata.core.constants.ConfigurationKeys;
import io.seata.core.protocol.RegisterRMRequest;
import io.seata.discovery.registry.FileRegistryServiceImpl;
import io.seata.discovery.registry.RegistryFactory;
import io.seata.discovery.registry.RegistryService;
import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Netty client pool manager.
 * netty 客户端管理池
 * @author slievrly
 * @author zhaojun
 */
class NettyClientChannelManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NettyClientChannelManager.class);

    private final ConcurrentMap<String, Object> channelLocks = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, NettyPoolKey> poolKeyMap = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Channel> channels = new ConcurrentHashMap<>();

    private final GenericKeyedObjectPool<NettyPoolKey, Channel> nettyClientKeyPool;

    private Function<String, NettyPoolKey> poolKeyFunction;

    /**
     * @param keyPoolableFactory
     * @param poolKeyFunction
     * @param clientConfig
     */
    NettyClientChannelManager(final NettyPoolableFactory keyPoolableFactory, final Function<String, NettyPoolKey> poolKeyFunction,
                                     final NettyClientConfig clientConfig) {
        nettyClientKeyPool = new GenericKeyedObjectPool<>(keyPoolableFactory);
        nettyClientKeyPool.setConfig(getNettyPoolConfig(clientConfig));
        this.poolKeyFunction = poolKeyFunction;
    }

    private GenericKeyedObjectPool.Config getNettyPoolConfig(final NettyClientConfig clientConfig) {
        GenericKeyedObjectPool.Config poolConfig = new GenericKeyedObjectPool.Config();
        poolConfig.maxActive = clientConfig.getMaxPoolActive();
        poolConfig.minIdle = clientConfig.getMinPoolIdle();
        poolConfig.maxWait = clientConfig.getMaxAcquireConnMills();
        poolConfig.testOnBorrow = clientConfig.isPoolTestBorrow();
        poolConfig.testOnReturn = clientConfig.isPoolTestReturn();
        poolConfig.lifo = clientConfig.isPoolLifo();
        return poolConfig;
    }

    /**
     * Get all channels registered on current Rpc Client.
     *
     * @return channels
     */
    ConcurrentMap<String, Channel> getChannels() {
        return channels;
    }

    /**
     * Acquire netty client channel connected to remote server.
     * 获取连接远端server的netty客户端通道
     * @param serverAddress server address
     * @return netty channel
     */
    Channel acquireChannel(String serverAddress) {
        Channel channelToServer = channels.get(serverAddress);
        if (channelToServer != null) {
            channelToServer = getExistAliveChannel(channelToServer, serverAddress);
            if (channelToServer != null) {
                return channelToServer;
            }
        }
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("will connect to " + serverAddress);
        }
        Object lockObj = CollectionUtils.computeIfAbsent(channelLocks, serverAddress, key -> new Object());
        synchronized (lockObj) {
            return doConnect(serverAddress);
        }
    }

    /**
     * Release channel to pool if necessary.
     *
     * @param channel channel
     * @param serverAddress server address
     */
    void releaseChannel(Channel channel, String serverAddress) {
        if (channel == null || serverAddress == null) { return; }
        try {
            synchronized (channelLocks.get(serverAddress)) {
                Channel ch = channels.get(serverAddress);
                if (ch == null) {
                    nettyClientKeyPool.returnObject(poolKeyMap.get(serverAddress), channel);
                    return;
                }
                if (ch.compareTo(channel) == 0) {
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("return to pool, rm channel:{}", channel);
                    }
                    destroyChannel(serverAddress, channel);
                } else {
                    nettyClientKeyPool.returnObject(poolKeyMap.get(serverAddress), channel);
                }
            }
        } catch (Exception exx) {
            LOGGER.error(exx.getMessage());
        }
    }

    /**
     * Destroy channel.
     * 销毁通道
     * @param serverAddress server address
     * @param channel channel
     */
    void destroyChannel(String serverAddress, Channel channel) {
        if (channel == null) { return; }
        try {
            if (channel.equals(channels.get(serverAddress))) {
                channels.remove(serverAddress);
            }
            nettyClientKeyPool.returnObject(poolKeyMap.get(serverAddress), channel);
        } catch (Exception exx) {
            LOGGER.error("return channel to rmPool error:{}", exx.getMessage());
        }
    }

    /**
     * Reconnect to remote server of current transaction service group.
     * 重连当前事物服务组的远端server
     * @param transactionServiceGroup transaction service group
     */
    void reconnect(String transactionServiceGroup) {
        List<String> availList = null;
        try {
            //从注册器获取事务分组的服务地址
            availList = getAvailServerList(transactionServiceGroup);
        } catch (Exception e) {
            LOGGER.error("Failed to get available servers: {}", e.getMessage(), e);
            return;
        }
        if (CollectionUtils.isEmpty(availList)) {
            RegistryService registryService = RegistryFactory.getInstance();
            String clusterName = registryService.getServiceGroup(transactionServiceGroup);

            if (StringUtils.isBlank(clusterName)) {
                LOGGER.error("can not get cluster name in registry config '{}{}', please make sure registry config correct",
                        ConfigurationKeys.SERVICE_GROUP_MAPPING_PREFIX,
                        transactionServiceGroup);
                return;
            }

            if (!(registryService instanceof FileRegistryServiceImpl)) {
                LOGGER.error("no available service found in cluster '{}', please make sure registry config correct and keep your seata server running", clusterName);
            }
            return;
        }
        for (String serverAddress : availList) {
            try {
                //建立连接通道
                acquireChannel(serverAddress);
            } catch (Exception e) {
                LOGGER.error("{} can not connect to {} cause:{}",FrameworkErrorCode.NetConnect.getErrCode(), serverAddress, e.getMessage(), e);
            }
        }
    }

    void invalidateObject(final String serverAddress, final Channel channel) throws Exception {
        nettyClientKeyPool.invalidateObject(poolKeyMap.get(serverAddress), channel);
    }

    /**
     * 注册连接TC通道
     * @param serverAddress
     * @param channel
     */
    void registerChannel(final String serverAddress, final Channel channel) {
        Channel channelToServer = channels.get(serverAddress);
        if (channelToServer != null && channelToServer.isActive()) {
            return;
        }
        channels.put(serverAddress, channel);
    }

    /**
     * 连接TC服务端
     * @param serverAddress
     * @return
     */
    private Channel doConnect(String serverAddress) {
        Channel channelToServer = channels.get(serverAddress);
        if (channelToServer != null && channelToServer.isActive()) {
            return channelToServer;
        }
        Channel channelFromPool;
        try {
            NettyPoolKey currentPoolKey = poolKeyFunction.apply(serverAddress);
            NettyPoolKey previousPoolKey = poolKeyMap.putIfAbsent(serverAddress, currentPoolKey);
            if (previousPoolKey != null && previousPoolKey.getMessage() instanceof RegisterRMRequest) {
                //TM 服务
                RegisterRMRequest registerRMRequest = (RegisterRMRequest) currentPoolKey.getMessage();
                ((RegisterRMRequest) previousPoolKey.getMessage()).setResourceIds(registerRMRequest.getResourceIds());
            }
            //创建通道对象
            channelFromPool = nettyClientKeyPool.borrowObject(poolKeyMap.get(serverAddress));
            channels.put(serverAddress, channelFromPool);
        } catch (Exception exx) {
            LOGGER.error("{} register RM failed.",FrameworkErrorCode.RegisterRM.getErrCode(), exx);
            throw new FrameworkException("can not register RM,err:" + exx.getMessage());
        }
        return channelFromPool;
    }

    /**
     * 从注册器获取事务分组的服务地址
     * @param transactionServiceGroup
     * @return
     * @throws Exception
     */
    private List<String> getAvailServerList(String transactionServiceGroup) throws Exception {
        List<InetSocketAddress> availInetSocketAddressList = RegistryFactory.getInstance()
                                                                            .lookup(transactionServiceGroup);
        if (CollectionUtils.isEmpty(availInetSocketAddressList)) {
            return Collections.emptyList();
        }

        return availInetSocketAddressList.stream()
                                         .map(NetUtil::toStringAddress)
                                         .collect(Collectors.toList());
    }

    /**
     * 获取存活通道
     * @param rmChannel
     * @param serverAddress
     * @return
     */
    private Channel getExistAliveChannel(Channel rmChannel, String serverAddress) {
        if (rmChannel.isActive()) {
            return rmChannel;
        } else {
            int i = 0;
            for (; i < NettyClientConfig.getMaxCheckAliveRetry(); i++) {
                try {
                    Thread.sleep(NettyClientConfig.getCheckAliveInternal());
                } catch (InterruptedException exx) {
                    LOGGER.error(exx.getMessage());
                }
                rmChannel = channels.get(serverAddress);
                if (rmChannel != null && rmChannel.isActive()) {
                    return rmChannel;
                }
            }
            if (i == NettyClientConfig.getMaxCheckAliveRetry()) {
                LOGGER.warn("channel {} is not active after long wait, close it.", rmChannel);
                //重试超次，则释放通道
                releaseChannel(rmChannel, serverAddress);
                return null;
            }
        }
        return null;
    }
}

