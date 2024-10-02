package com.zealsinger.user.run;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.zealsinger.book.framework.common.constant.RedisConstant;
import com.zealsinger.book.framework.common.utils.JsonUtil;
import com.zealsinger.user.domain.entity.Permission;
import com.zealsinger.user.domain.entity.Role;
import com.zealsinger.user.domain.entity.RolePermission;
import com.zealsinger.user.mapper.PermissionMapper;
import com.zealsinger.user.mapper.RoleMapper;
import com.zealsinger.user.mapper.RolePermissionMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

        // 查询出所有角色
        List<Role> roleDOS = roleMapper.selectEnabledList();

        if (CollUtil.isNotEmpty(roleDOS)) {
            // 拿到所有角色的 ID
            List<Long> roleIds = roleDOS.stream().map(Role::getId).toList();

            // 根据角色 ID, 批量查询出所有角色对应的权限
            LambdaQueryWrapper<RolePermission> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.in(RolePermission::getRoleId, roleIds);
            List<RolePermission> rolePermissionDOS = rolePermissionMapper.selectList(queryWrapper);
            // 按角色 ID 分组, 每个角色 ID 对应多个权限 ID
            Map<Long, List<Long>> roleIdPermissionIdsMap = rolePermissionDOS.stream().collect(
                    Collectors.groupingBy(RolePermission::getRoleId,
                            Collectors.mapping(RolePermission::getPermissionId, Collectors.toList()))
            );

            // 查询 APP 端所有被启用的权限
            List<Permission> permissionDOS = permissionMapper.selectAppEnabledList();
            // 权限 ID - 权限 DO
            Map<Long, Permission> permissionIdDOMap = permissionDOS.stream().collect(
                    Collectors.toMap(Permission::getId, permissionDO -> permissionDO)
            );

            // 组织 角色ID-权限 关系
            Map<String, List<String>> roleKeyPermissionsMap = Maps.newHashMap();

            // 循环所有角色
            roleDOS.forEach(roleDO -> {
                // 当前角色 ID
                Long roleId = roleDO.getId();
                // 当前角色 roleKey
                String roleKey = roleDO.getRoleKey();
                // 当前角色 ID 对应的权限 ID 集合
                List<Long> permissionIds = roleIdPermissionIdsMap.get(roleId);
                if (CollUtil.isNotEmpty(permissionIds)) {
                    List<String> permissionKeys = Lists.newArrayList();
                    permissionIds.forEach(permissionId -> {
                        // 根据权限 ID 获取具体的权限 DO 对象
                        Permission permissionDO = permissionIdDOMap.get(permissionId);
                        permissionKeys.add(permissionDO.getPermissionKey());
                    });
                    roleKeyPermissionsMap.put(roleKey, permissionKeys);
                }
            });

            // 同步至 Redis 中，方便后续网关查询 Redis, 用于鉴权
            roleKeyPermissionsMap.forEach((roleKey, permissions) -> {
                String key = RedisConstant.buildRolePermissionsKey(roleKey);
                redisTemplate.opsForValue().set(key, JsonUtil.ObjToJsonString(permissions));
            });
        }

        log.info("==> 服务启动，成功同步角色权限数据到 Redis 中...");
    }
}
