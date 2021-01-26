# Client(TM, RM)

## SeataAutoConfiguration   

## GlobalTransactionScanner  
io.seata.spring.annotation.GlobalTransactionScanner#initClient


### TMClient  
io.seata.tm.TMClient#init(java.lang.String, java.lang.String, java.lang.String, java.lang.String)

### ClientOnResponseProcessor  
<!-- TODO -->
io.seata.core.rpc.processor.client.ClientOnResponseProcessor#process

###  NettyClientBootstrap
io.seata.core.rpc.netty.NettyClientBootstrap#start

#### ProtocolV1Decoder

#### ProtocolV1Encoder

#### ClientHandler
io.seata.core.rpc.netty.AbstractNettyRemotingClient.ClientHandler


### RMClient


io.seata.rm.RMClient#init
####  RmNettyRemotingClient
io.seata.core.rpc.netty.RmNettyRemotingClient#init


##### DefaultResourceManager

* BranchType.AT， DataSourceManager
* TCC,TCCResourceManager
* SAGA,SagaResourceManager
* XA, ResourceManagerXA


#### DataSourceProxy
io.seata.rm.datasource.DataSourceProxy#init

io.seata.spring.annotation.datasource.DataSourceProxyHolder#putDataSource

io.seata.spring.annotation.datasource.SeataDataSourceBeanPostProcessor#postProcessAfterInitialization

##### DefaultRMHandler
io.seata.rm.DefaultRMHandler#initRMHandlers

* AT:RMHandlerAT
* TCC:RMHandlerTCC
* SAGA:RMHandlerSaga
* XA:RMHandlerXA

##### RMHandlerAT

提交消息已处理，回滚消息处理

<!-- TODO -->

##### RmBranchCommitProcessor

#### RmBranchRollbackProcessor

#### RmUndoLogProcessor

####  ClientOnResponseProcessor


#### 
#### 

# Server(TC)