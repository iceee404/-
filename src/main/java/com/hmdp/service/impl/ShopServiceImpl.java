package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //从redis里查询商铺缓存
        String shopJason = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(shopJason)) {
            //存在，直接返回
            Shop shop = JSONUtil.toBean(shopJason, Shop.class);
            return Result.ok(shop);
        }

        //不存在，去数据库查
        Shop shop = getById(id);
        if (shop == null) {
            //这里也可以缓存null值，防止穿透
            return Result.fail("店铺不存在!");
        }

//       try{
//           //不存在，在去数据库查之前先 获取互斥锁
//           String lockKey = "lock:shop" + id;
//           boolean isLock = tryLock(lockKey);
//
//           if (!isLock ){
//               //没有锁，就休眠
//
//                   Thread.sleep(50);
//
//               //休眠结束，再去查一遍看看拿锁的人查询好并写进redis了吗，这里直接递归执行
//               return queryById(id);
//           }
//
//           shop = getById(id);
//           //序列化好并写入redis
//           stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),2L, TimeUnit.MINUTES);
//           //写入redis后释放锁
//           unlock(lockKey);
//       }catch (InterruptedException e){
//           throw new RuntimeException(e);
//       }
//
//        //返回前端
//        return Result.ok(shop);
//

        //序列化好并写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),2L, TimeUnit.MINUTES);

        //返回前端
        return Result.ok(shop);
    }

    @Override
    public void modifyById(Shop shop) {
        String key = CACHE_SHOP_KEY + shop.getId();
        updateById(shop);
        stringRedisTemplate.delete(key);


    }



    //获取锁
    private boolean tryLock(String key){
        //这里锁要加TTL防止死锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        //使用hutool工具包里的 布尔工具再进行包装，防止直接返回flag后有自动拆箱问题导致不对
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unlock(String key  ){
        stringRedisTemplate.delete(key);
    }


    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {

        if(true){
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页。结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(shops);
    }
}
