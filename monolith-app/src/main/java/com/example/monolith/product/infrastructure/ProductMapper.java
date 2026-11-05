package com.example.monolith.product.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** product 的 Mapper：只用 BaseMapper 自带的单表方法 */
@Mapper
public interface ProductMapper extends BaseMapper<ProductPO> {}
