package com.parkease.auth.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    // Covers all methods in service layer
    @Pointcut("execution(* com.parkease.auth.service.*.*(..))")
    public void serviceLayer() {}

    // Covers all methods in controller layer
    @Pointcut("execution(* com.parkease.auth.controller.*.*(..))")
    public void controllerLayer() {}

    @Around("serviceLayer() || controllerLayer()")
    public Object logMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {

        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        log.info(">>> Entering  [{}.{}]", className, methodName);

        long startTime = System.currentTimeMillis();

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Exception ex) {
            log.error("!!! Exception in [{}.{}] : {}", className, methodName, ex.getMessage());
            throw ex;
        }

        long timeTaken = System.currentTimeMillis() - startTime;
        log.info("<<< Exiting   [{}.{}] | Time: {} ms", className, methodName, timeTaken);

        return result;
    }
}
