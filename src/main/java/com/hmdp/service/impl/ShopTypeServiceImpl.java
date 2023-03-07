package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public List<ShopType> queryTypeList() {
        String key = CACHE_SHOPTYPE_KEY;
        //从redis里查询商铺缓存
        String shopTypeJason = stringRedisTemplate.opsForValue().get(key);



        //判断是否存在
        if (StrUtil.isNotBlank(shopTypeJason)) {
            //存在，直接返回
            JSONArray objects = JSONUtil.parseArray(shopTypeJason);
            List<ShopType> shopTypeList = JSONUtil.toList(objects, ShopType.class);
            return shopTypeList;
        }

        //不存在，去数据库查
        List<ShopType> shopTypeList = list();
        if (shopTypeList == null) {
            return null;
        }

        //序列化好并写入redis
        JSONUtil.toJsonStr(shopTypeList);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shopTypeList));

        //返回前端
        return shopTypeList;


    }
}
