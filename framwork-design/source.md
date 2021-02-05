# Client(TM, RM)

## SeataAutoConfiguration   

## GlobalTransactionScanner  
io.seata.spring.annotation.GlobalTransactionScanner#initClient

io.seata.spring.annotation.GlobalTransactionScanner#wrapIfNecessary



<!-- 开启全局事务 -->
### GlobalTransactional
io.seata.spring.annotation.GlobalTransactional  

### GlobalTransactionalInterceptor
io.seata.spring.annotation.GlobalTransactionalInterceptor#invoke

#### TransactionalTemplate
io.seata.tm.api.TransactionalTemplate#execute

#### DefaultGlobalTransaction
io.seata.tm.api.DefaultGlobalTransaction

<!-- 全局事务 -->




### TMClient  
io.seata.tm.TMClient#init(java.lang.String, java.lang.String, java.lang.String, java.lang.String)


#### TmNettyRemotingClient
io.seata.core.rpc.netty.AbstractNettyRemoting#processMessage
### ClientOnResponseProcessor  
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

io.seata.core.rpc.netty.AbstractNettyRemoting#processMessage
##### DefaultResourceManager

* BranchType.AT， DataSourceManager
* TCC,TCCResourceManager
* SAGA,SagaResourceManager
* XA, ResourceManagerXA

SeataAutoDataSourceProxyCreator
SeataAutoDataSourceProxyAdvice， SeataDataSourceProxy

#### DataSourceProxy

SeataDataSourceBeanPostProcessor

io.seata.rm.datasource.DataSourceProxy#init

io.seata.spring.annotation.datasource.DataSourceProxyHolder#putDataSource

io.seata.spring.annotation.datasource.SeataDataSourceBeanPostProcessor#postProcessAfterInitialization

##### ConnectionProxy

<!-- 基于DUBBO的AT服务，主要根据RootContext的XID和事务分支类型，决定是否处于全局事务中，及执行的事务类型 -->
ApacheDubboTransactionPropagationFilter
org.apache.dubbo.rpc.RpcContext

io.seata.rm.datasource.ConnectionProxy#commit  
io.seata.rm.datasource.ConnectionProxy#processGlobalTransactionCommit  

io.seata.rm.datasource.ConnectionProxy#register  
io.seata.rm.datasource.ConnectionProxy#rollback  
io.seata.rm.datasource.ConnectionProxy#setAutoCommit  



<!-- 构建undolog -->
io.seata.rm.datasource.exec.BaseTransactionalExecutor#prepareUndoLog

io.seata.rm.datasource.ConnectionProxy#appendUndoLog
io.seata.rm.datasource.ConnectionProxy#appendLockKey


**AbstractConnectionProxy**
io.seata.rm.datasource.AbstractConnectionProxy#prepareStatement(java.lang.String, int, int)

**PreparedStatementProxy**
io.seata.rm.datasource.PreparedStatementProxy#executeUpdate

**ExecuteTemplate**
io.seata.rm.datasource.exec.ExecuteTemplate#execute(io.seata.rm.datasource.StatementProxy<S>, io.seata.rm.datasource.exec.StatementCallback<T,S>, java.lang.Object...)

##### BaseTransactionalExecutor


<!-- 绑定全局事务id， 执行DML操作 -->
io.seata.rm.datasource.exec.BaseTransactionalExecutor#execute
<!-- TODO  -->
io.seata.rm.datasource.exec.AbstractDMLBaseExecutor#doExecute

* MySQLInsertExecutor
* UpdateExecutor
* DeleteExecutor
* SelectForUpdateExecutor
* PlainExecutor
* MultiExecutor

**DruidSQLRecognizerFactoryImpl** 
io.seata.sqlparser.druid.DruidSQLRecognizerFactoryImpl#create

**MySQLOperateRecognizerHolder**
* MySQLDeleteRecognizer
* MySQLInsertRecognizer
* MySQLUpdateRecognizer
* MySQLSelectForUpdateRecognizer
##### DefaultRMHandler
io.seata.rm.DefaultRMHandler#initRMHandlers

* AT:RMHandlerAT
* TCC:RMHandlerTCC
* SAGA:RMHandlerSaga
* XA:RMHandlerXA

##### RMHandlerAT
io.seata.rm.RMHandlerAT#handle

<!-- 提交消息，回滚消息处理；委托给ConnectionProxy#setAutoCommit/ -->

io.seata.rm.AbstractRMHandler#doBranchCommit
io.seata.rm.AbstractRMHandler#doBranchRollback
##### RmBranchCommitProcessor
io.seata.rm.datasource.DataSourceManager#branchCommit 
#### RmBranchRollbackProcessor

io.seata.rm.datasource.undo.AbstractUndoLogManager#undo
#### RmUndoLogProcessor

####  ClientOnResponseProcessor

# Server(TC)


```sh
exec "$JAVACMD" $JAVA_OPTS -server -Xmx2048m -Xms2048m -Xmn1024m -Xss512k -XX:SurvivorRatio=10 -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=256m -XX:MaxDirectMemorySize=1024m -XX:-OmitStackTraceInFastThrow -XX:-UseAdaptiveSizePolicy -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath="$BASEDIR"/logs/java_heapdump.hprof -XX:+DisableExplicitGC -XX:+CMSParallelRemarkEnabled -XX:+UseCMSInitiatingOccupancyOnly -XX:CMSInitiatingOccupancyFraction=75 -Xloggc:"$BASEDIR"/logs/seata_gc.log -verbose:gc -Dio.netty.leakDetectionLevel=advanced -Dlogback.color.disable-for-bat=true \
  -classpath "$CLASSPATH" \
  -Dapp.name="seata-server" \
  -Dapp.pid="$$" \
  -Dapp.repo="$REPO" \
  -Dapp.home="$BASEDIR" \
  -Dbasedir="$BASEDIR" \
  io.seata.server.Server \
  "$@"
```

```cmd
seata-server.bat -p 8091 -h 127.0.0.1 -m db
```

## Server
io.seata.server.Server

### SessionHolder
io.seata.server.session.SessionHolder#init

会话管理器
DataBaseSessionManager
FileSessionManager
RedisSessionManager

 锁

DataBaseLockManager
FileLockManager
RedisLockManager
#### GlobalSession

#### BranchSession

RowLock
### DefaultCoordinator

io.seata.server.coordinator.DefaultCoordinator#onRequest
#### DefaultCore

ATCore  
XACore  
TccCore  
SagaCore  

<!-- 核心 -->

处理所有事务的请求，开启，提交，回滚，注册

### NettyRemotingServer

io.seata.core.rpc.netty.NettyRemotingServer#init

ServerOnRequestProcessor  
ServerOnResponseProcessor  
RegRmProcessor  
RegTmProcessor  
ServerHeartbeatProcessor  













# 架构
...
## 流程
注册RM，TM 到TC
事务开始
事务提交
事务回滚



# spring-cloud-starter-alibaba-seata
<!-- todo -->