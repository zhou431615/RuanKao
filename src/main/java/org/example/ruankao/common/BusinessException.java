package org.example.ruankao.common;

/**
 * 业务异常：由全局异常处理器统一转换为 400 响应。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
