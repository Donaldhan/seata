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
package io.seata.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.seata.common.DefaultValues;
import io.seata.tm.api.transaction.Propagation;
import org.aopalliance.intercept.MethodInvocation;

/**
 * The interface Global transactional.
 *
 * @author slievrly
 * // the scanner for TM, GlobalLock, and TCC mode
 * Tm ，全局锁， TCC 模式
 * @see io.seata.spring.annotation.GlobalTransactionScanner#wrapIfNecessary(Object, String, Object)
 *
 * // TM: the interceptor of TM
 * TM 全局事务拦截器
 * @see io.seata.spring.annotation.GlobalTransactionalInterceptor#handleGlobalTransaction(MethodInvocation, GlobalTransactional)
 *
 * // RM: the interceptor of GlobalLockLogic and AT/XA mode
 * RM： GlobalLockLogic， AT/XA模式拦截器
 * @see io.seata.spring.annotation.datasource.SeataAutoDataSourceProxyAdvice#invoke(MethodInvocation)
 *
 * // RM: the interceptor of TCC mode
 * RM： TCC模式拦截器
 * @see io.seata.spring.tcc.TccActionInterceptor#invoke(MethodInvocation)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD,ElementType.TYPE})
@Inherited
public @interface GlobalTransactional {

    /**
     * Global transaction timeoutMills in MILLISECONDS.
     * If client.tm.default-global-transaction-timeout is configured, It will replace the DefaultValues.DEFAULT_GLOBAL_TRANSACTION_TIMEOUT.
     *
     * @return timeoutMills in MILLISECONDS.
     */
    int timeoutMills() default DefaultValues.DEFAULT_GLOBAL_TRANSACTION_TIMEOUT;

    /**
     * Given name of the global transaction instance.
     *
     * @return Given name.
     */
    String name() default "";

    /**
     * roll back for the Class
     * @return
     */
    Class<? extends Throwable>[] rollbackFor() default {};

    /**
     * roll back for the class name
     * @return
     */
    String[] rollbackForClassName() default {};

    /**
     * not roll back for the Class
     * @return
     */
    Class<? extends Throwable>[] noRollbackFor() default {};

    /**
     * not roll back for the class name
     * @return
     */
    String[] noRollbackForClassName() default {};

    /**
     * the propagation of the global transaction
     * 默认：如果当前事物存在，则在当前事务下执行，否则创建一个新的事务
     * @return
     */
    Propagation propagation() default Propagation.REQUIRED;

    /**
     * customized global lock retry internal(unit: ms)
     * you may use this to override global config of "client.rm.lock.retryInterval"
     * note: 0 or negative number will take no effect(which mean fall back to global config)
     * @return
     */
    int lockRetryInternal() default 0;

    /**
     * customized global lock retry times
     * you may use this to override global config of "client.rm.lock.retryTimes"
     * note: negative number will take no effect(which mean fall back to global config)
     * @return
     */
    int lockRetryTimes() default -1;
}
