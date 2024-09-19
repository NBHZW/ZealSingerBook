package com.zealsinger.aspect;

import com.zealsinger.book.framework.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

@Aspect
@Slf4j
public class ApiOperationLogAspect {
    @Pointcut("@annotation(com.zealsinger.aspect.ZealLog)")
    public void apiOperationLog(){}


    /**
     * 环绕切面 记录入参 出参 方法名 耗时等等
     */
    @Around("apiOperationLog()")
    public Object doAround(ProceedingJoinPoint point) throws Throwable {
        String className = point.getTarget().getClass().getName(); // 类名
        String methodName = point.getSignature().getName(); // 方法名
        Object[] args = point.getArgs();  // 入参
        String argsStr = Arrays.stream(args).map(toJsonStr()).collect(Collectors.joining(","));  // 将入参转换为String类型
        String description = getDescriptionByAnnotation(point);
        long startTime = System.currentTimeMillis();
        log.info("====== 请求开始: [{}], 入参: {}, 请求类: {}, 请求方法: {} =================================== ",
                description, argsStr, className, methodName);

        // 执行切入点方法
        Object result = point.proceed();

        // 执行耗时
        long executionTime = System.currentTimeMillis() - startTime;

        // 打印出参等相关信息
        log.info("====== 请求结束: [{}], 耗时: {}ms, 出参: {} =================================== ",
                description, executionTime, JsonUtil.ObjToJsonString(result));

        return result;
    }

    /**
     * 从注解中获取description
     * @param point
     * @return
     */
    private String getDescriptionByAnnotation(ProceedingJoinPoint point){
        MethodSignature signature = (MethodSignature) point.getSignature();
        ZealLog zealLog = signature.getMethod().getAnnotation(ZealLog.class);
        if(zealLog!=null){
            return zealLog.description();
        }
        return "";
    }


    private Function<Object, String> toJsonStr(){
        return JsonUtil::ObjToJsonString;
    }

}
