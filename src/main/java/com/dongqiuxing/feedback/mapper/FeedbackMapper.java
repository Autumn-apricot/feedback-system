package com.dongqiuxing.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dongqiuxing.feedback.entity.Feedback;
import org.apache.ibatis.annotations.Mapper;

/**
 * 意见表 Mapper
 *
 * <p>继承 MyBatis-Plus 的 BaseMapper 后，单表 CRUD 无需再写 XML 或注解 SQL，
 * selectList / insert / updateById 等方法由框架自动生成。</p>
 */
@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {
}
