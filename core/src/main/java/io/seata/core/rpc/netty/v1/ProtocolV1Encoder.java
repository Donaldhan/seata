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
package io.seata.core.rpc.netty.v1;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.seata.common.loader.EnhancedServiceLoader;
import io.seata.core.serializer.Serializer;
import io.seata.core.compressor.Compressor;
import io.seata.core.compressor.CompressorFactory;
import io.seata.core.protocol.ProtocolConstants;
import io.seata.core.protocol.RpcMessage;
import io.seata.core.serializer.SerializerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * <pre>
 * 0     1     2     3     4     5     6     7     8     9    10     11    12    13    14    15    16
 * +-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+-----+
 * |   magic   |Proto|     Full length       |    Head   | Msg |Seria|Compr|     RequestId         |
 * |   code    |colVer|    (head+body)      |   Length  |Type |lizer|ess  |                       |
 * +-----------+-----------+-----------+-----------+-----------+-----------+-----------+-----------+
 * |                                                                                               |
 * |                                   Head Map [Optional]                                         |
 * +-----------+-----------+-----------+-----------+-----------+-----------+-----------+-----------+
 * |                                                                                               |
 * |                                         body                                                  |
 * |                                                                                               |
 * |                                        ... ...                                                |
 * +-----------------------------------------------------------------------------------------------+
 * </pre>
 * <p>
 * <li>Full Length: include all data </li>
 * <li>Head Length: include head data from magic code to head map. </li>
 * <li>Body Length: Full Length - Head Length</li>
 * </p>
 * https://github.com/seata/seata/issues/893
 * 编码器
 * @author Geng Zhang
 * @see ProtocolV1Decoder
 * @since 0.7.0
 */
public class ProtocolV1Encoder extends MessageToByteEncoder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolV1Encoder.class);

    /**
     * 协议魔数+协议版本+总长度（head+body）+消息类型+消息编码器+消息压缩器+消息id+头部+body
     * @param ctx
     * @param msg
     * @param out
     */
    @Override
    public void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) {
        try {
            if (msg instanceof RpcMessage) {
                RpcMessage rpcMessage = (RpcMessage) msg;
                //head+body length
                int fullLength = ProtocolConstants.V1_HEAD_LENGTH;
                //head length
                int headLength = ProtocolConstants.V1_HEAD_LENGTH;
                //消息类型
                byte messageType = rpcMessage.getMessageType();
                //协议魔数
                out.writeBytes(ProtocolConstants.MAGIC_CODE_BYTES);
                //协议版本
                out.writeByte(ProtocolConstants.VERSION);
                // full Length(4B) and head length(2B) will fix in the end. 
                out.writerIndex(out.writerIndex() + 6);
                out.writeByte(messageType);
                //消息编码器
                out.writeByte(rpcMessage.getCodec());
                //消息压缩器
                out.writeByte(rpcMessage.getCompressor());
                //消息id
                out.writeInt(rpcMessage.getId());

                // direct write head with zero-copy 头部数据
                Map<String, String> headMap = rpcMessage.getHeadMap();
                if (headMap != null && !headMap.isEmpty()) {
                    int headMapBytesLength = HeadMapSerializer.getInstance().encode(headMap, out);
                    headLength += headMapBytesLength;
                    fullLength += headMapBytesLength;
                }

                byte[] bodyBytes = null;
                if (messageType != ProtocolConstants.MSGTYPE_HEARTBEAT_REQUEST
                        && messageType != ProtocolConstants.MSGTYPE_HEARTBEAT_RESPONSE) {
                    // heartbeat has no body 序列化请求数据，并压缩
                    Serializer serializer = EnhancedServiceLoader.load(Serializer.class, SerializerType.getByCode(rpcMessage.getCodec()).name());
                    bodyBytes = serializer.serialize(rpcMessage.getBody());
                    Compressor compressor = CompressorFactory.getCompressor(rpcMessage.getCompressor());
                    bodyBytes = compressor.compress(bodyBytes);
                    fullLength += bodyBytes.length;
                }

                if (bodyBytes != null) {
                    out.writeBytes(bodyBytes);
                }

                // fix fullLength and headLength
                int writeIndex = out.writerIndex();
                // skip magic code(2B) + version(1B) 跳过魔数和版本
                out.writerIndex(writeIndex - fullLength + 3);
                out.writeInt(fullLength);
                out.writeShort(headLength);
                //buffer writer index
                out.writerIndex(writeIndex);
            } else {
                throw new UnsupportedOperationException("Not support this class:" + msg.getClass());
            }
        } catch (Throwable e) {
            LOGGER.error("Encode request error!", e);
        }
    }
}
