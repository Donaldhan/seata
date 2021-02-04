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
package io.seata.tm.api.transaction;

import io.seata.tm.api.TransactionalExecutor;

/**
 * Propagation level of global transactions.
 * 全部事务传播等级
 * @author haozhibei
 * @see io.seata.spring.annotation.GlobalTransactional#propagation() // TM annotation
 * @see io.seata.spring.annotation.GlobalTransactionalInterceptor#invoke(MethodInvocation) // the interceptor of TM
 * @see io.seata.tm.api.TransactionalTemplate#execute(TransactionalExecutor) // the transaction template of TM
 */
public enum Propagation {
    /**
     * The REQUIRED.
     * The default propagation.
     *
     * <p>
     * If transaction is existing, execute with current transaction,
     * else execute with new transaction.
     * </p>
     * 如果当前事物存在，则在当前事务下执行，否则创建一个新的事务
     * <p>
     * The logic is similar to the following code:
     * <code><pre>
     *     if (tx == null) {
     *         try {
     *             tx = beginNewTransaction(); // begin new transaction, is not existing
     *             Object rs = business.execute(); // execute with new transaction
     *             commitTransaction(tx);
     *             return rs;
     *         } catch (Exception ex) {
     *             rollbackTransaction(tx);
     *             throw ex;
     *         }
     *     } else {
     *         return business.execute(); // execute with current transaction
     *     }
     * </pre></code>
     * </p>
     */
    REQUIRED,

    /**
     * The REQUIRES_NEW.
     *
     * <p>
     * If transaction is existing, suspend it, and then execute business with new transaction.
     * </p>
     * 存在事务，则中断，创建一个新的事务，执行业务
     * <p>
     * The logic is similar to the following code:
     * <code><pre>
     *     try {
     *         if (tx != null) {
     *             suspendedResource = suspendTransaction(tx); // suspend current transaction
     *         }
     *         try {
     *             tx = beginNewTransaction(); // begin new transaction
     *             Object rs = business.execute(); // execute with new transaction
     *             commitTransaction(tx);
     *             return rs;
     *         } catch (Exception ex) {
     *             rollbackTransaction(tx);
     *             throw ex;
     *         }
     *     } finally {
     *         if (suspendedResource != null) {
     *             resumeTransaction(suspendedResource); // resume transaction
     *         }
     *     }
     * </pre></code>
     * </p>
     */
    REQUIRES_NEW,

    /**
     * The NOT_SUPPORTED.
     *
     * <p>
     * If transaction is existing, suspend it, and then execute business without transaction.
     * </p>
     * 如果事务只存在，则中断， 然后直接在无事务环境中执行业务
     * <p>
     * The logic is similar to the following code:
     * <code><pre>
     *     try {
     *         if (tx != null) {
     *             suspendedResource = suspendTransaction(tx); // suspend current transaction
     *         }
     *         return business.execute(); // execute without transaction
     *     } finally {
     *         if (suspendedResource != null) {
     *             resumeTransaction(suspendedResource); // resume transaction
     *         }
     *     }
     * </pre></code>
     * </p>
     */
    NOT_SUPPORTED,

    /**
     * The SUPPORTS.
     *
     * <p>
     * If transaction is not existing, execute without global transaction,
     * else execute business with current transaction.
     * </p>
     * 如果事务不存在，在无全局事务下，执行业务与，否则在当前事务下执行业务
     * <p>
     * The logic is similar to the following code:
     * <code><pre>
     *     if (tx != null) {
     *         return business.execute(); // execute with current transaction
     *     } else {
     *         return business.execute(); // execute without transaction
     *     }
     * </pre></code>
     * </p>
     */
    SUPPORTS,

    /**
     * The NEVER.
     *
     * <p>
     * If transaction is existing, throw exception,
     * else execute business without transaction.
     * </p>
     * 如果存在事务，则抛出异常，否则非事务环境中，执行业务
     * <p>
     * The logic is similar to the following code:
     * <code><pre>
     *     if (tx != null) {
     *         throw new TransactionException("existing transaction");
     *     }
     *     return business.execute(); // execute without transaction
     * </pre></code>
     * </p>
     */
    NEVER,

    /**
     * The MANDATORY.
     *
     * <p>
     * If transaction is not existing, throw exception,
     * else execute business with current transaction.
     * </p>
     * 事务不存在，则抛出异常，或者在当前事务环境下，执行事务
     * <p>
     * The logic is similar to the following code:
     * <code><pre>
     *     if (tx == null) {
     *         throw new TransactionException("not existing transaction");
     *     }
     *     return business.execute(); // execute with current transaction
     * </pre></code>
     * </p>
     */
    MANDATORY
}
