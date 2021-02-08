![seata-framwork-design](/image/seata/seata-framework-design.png)

事务管理器TM

资源管理器RM

事务协调器TC

会话管理器： 全局会话、分支会话

## 全局事务
**begin**
1. TM开启一个全局事务（->TC），TC生成一个全局事务（事务上下文[RootContext]）；
2. TM 执行实际的业务开始；
3. RM加入全局事务（Dubbo Filter RPCContext），注册事物分支, TC保存事务分支及事务行锁（包括需要加锁的行主键Key）；
4. RM执行本地事务（事务执行器TransactionalExecutor、连接代理ConnectionProxy）；并存储事务undo log（关联行数据的前后镜像）到本地重做日志表，并上报分支事务状态到TC；
5. 待所有分支事务执行完（TM实际业务结束）；
6. 如果在所有RM的事务分支，执行过程中没有异常，走7， 否则走13；
**commit**
7. TM发送提交全局事务请求到TC；
8. TC发送分支事务提交请求到所有的RM事务分支；
9. RM删除Undo log；
10. RM事务分支提交成功，TC从全局事务中、删除事务分支，同时从分支事务表中删除；
11. 待所有RM处理完（分支事务提交请求）， TC结束全局事务；
12. 更新全局事务状态为已提交{@link GlobalStatus#Committed}，释放全局事务相关的锁，并从会话管理器中移除全局会话；
**rollback**
13. 异常发生，TM发起事务回滚请求到TC；
14. TC发送分支事务回滚请求到所有RM；
15. RM根据重做日志（数据前后镜像），回滚数据并删除undo log；
16. 待所有RM回滚完，TC结束全局；
17. 更新全局事务回滚（回滚成功Rollbacked，或回滚超时TimeoutRollbacked），释放全局事务相关的锁，并从会话管理器中移除全局会话，同时全局事务表中删除；



## 事务的回滚
1. 如果是插入操作，根据后镜像删除对应的数据即可；
2. 如果为删除操作，则根据前镜像插入对应的记录；
3. 如果为更新操作，根据后镜像检查数据，与当前数据库记录一直，则需要进行恢复数据（Update）
4. select for update；


### 行记录数据校验， 判断是否需要继续undo操作；情况如下：
1. 如果前后的镜像快照一样，则不需要做任何undo操作
2. 当前镜像快照和before镜像，相等，则不需要做任何操作
3. 当前镜像快照和after镜像一致，则需要进undo操作



RM