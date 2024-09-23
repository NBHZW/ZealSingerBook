package com.zealsinger.zealsingerbookauth.run;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.collect.Maps;
import com.zealsinger.book.framework.common.util.JsonUtil;
import com.zealsinger.zealsingerbookauth.constant.RedisConstant;
import com.zealsinger.zealsingerbookauth.constant.RoleConstants;
import com.zealsinger.zealsingerbookauth.domain.entity.Permission;
import com.zealsinger.zealsingerbookauth.domain.entity.Role;
import com.zealsinger.zealsingerbookauth.domain.entity.RolePermission;
import com.zealsinger.zealsingerbookauth.mapper.PermissionMapper;
import com.zealsinger.zealsingerbookauth.mapper.RoleMapper;
import com.zealsinger.zealsingerbookauth.mapper.RolePermissionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 初始化redis  将role-permission 角色-权限信息加载到redis中
 * @author ZealSinger
 */
@Component
@Slf4j
public class PushRolePermissionsRedisRunner implements ApplicationRunner {
    @Resource
    private RoleMapper roleMapper;

    @Resource
    private PermissionMapper permissionMapper;

    @Resource
    private RolePermissionMapper rolePermissionMapper;

    @Resource
    private RedisTemplate<String,String> redisTemplate;

    // 权限同步标记 Key  加锁保证单独使用
    private static final String PUSH_PERMISSION_FLAG = "push_permission_flag";

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 是否能够同步数据: 原子操作，只有在键 PUSH_PERMISSION_FLAG 不存在时，才会设置该键的值为 "1"，并设置过期时间为 1 天
        boolean canPushed = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(PUSH_PERMISSION_FLAG, "1", 1, TimeUnit.DAYS));
        // 如果无法同步权限数据
        if (!canPushed) {
            log.warn("==> 角色权限数据已经同步至 Redis 中，不再同步...");
            return;
        }

        log.info("==> 服务启动，开始同步角色权限数据到 Redis 中...");

        // 所有可用角色
        List<Role> enableRoleList = roleMapper.selectEnabledList();
        // 按照角色分类，将同一角色的权限放到一起为List 然后一起存到redis中
        if(!CollectionUtil.isEmpty(enableRoleList)){
            List<Long> enableRoleIdList = enableRoleList.stream().map(Role::getId).toList();
            // 获得所有角色对应的所有权限
            LambdaQueryWrapper<RolePermission> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.in(RolePermission::getRoleId, enableRoleIdList);
            List<RolePermission> rolePermissions = rolePermissionMapper.selectList(queryWrapper);
            // 按照角色进行分类 将对应的权限id整合为List  得到 roleId--权限ID的List 关系
            Map<Long, List<Long>> roleIdPermissionIdsMap = rolePermissions.stream().collect(
                    Collectors.groupingBy(RolePermission::getRoleId,
                            Collectors.mapping(RolePermission::getPermissionId, Collectors.toList()))
            );
            // 查询 APP 端所有被启用的权限  得到可用的 权限ID--对应的权限
            List<Permission> enablePermissionList = permissionMapper.selectAppEnabledList();
            Map<Long,Permission> enablePermissionMap = enablePermissionList.stream().collect(
                    Collectors.toMap(Permission::getId,permission -> permission)
            );

            // 组织 角色ID-权限 关系
            Map<Long, List<Permission>> roleIdPermissionMap = Maps.newHashMap();
            enableRoleIdList.forEach(roleId->{
                List<Long> permissionIdList = roleIdPermissionIdsMap.get(roleId);
                List<Permission> permissionList = new ArrayList<>();
                if(CollectionUtil.isNotEmpty(permissionIdList)){
                    for (Long permissionId : permissionIdList) {
                        Permission permission = enablePermissionMap.get(permissionId);
                        permissionList.add(permission);
                    }
                }
                if(!permissionIdList.isEmpty()){
                    roleIdPermissionMap.put(roleId,permissionList);
                }
            });

            roleIdPermissionMap.forEach((key,value)->{
                redisTemplate.opsForValue().set(RedisConstant.buildRolePermissionsKey(key),JsonUtil.ObjToJsonString(value));
            });
        }


        log.info("==> 服务启动，成功同步角色权限数据到 Redis 中...");
    }
}
