package org.nesc.ecbd.entity;

import com.baomidou.mybatisplus.annotations.TableField;
import com.baomidou.mybatisplus.annotations.TableId;
import com.baomidou.mybatisplus.annotations.TableName;
import com.baomidou.mybatisplus.enums.IdType;
/**
 * 
 * @author Jacob Cao
 * 
 * 2019年1月17日
 */
@TableName("rdb_analyze_result")
public class RDBAnalyzeResult {
	@TableId(value="id",type=IdType.AUTO)
	private Long id;
	@TableField(value="schedule_id")
	private Long scheduleId;
	@TableField(value="redis_info_id")
	private Long redisInfoId;
	private String result;
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public Long getScheduleId() {
		return scheduleId;
	}
	public void setScheduleId(Long scheduleId) {
		this.scheduleId = scheduleId;
	}
	public Long getRedisInfoId() {
		return redisInfoId;
	}
	public void setRedisInfoId(Long redisInfoId) {
		this.redisInfoId = redisInfoId;
	}
	public String getResult() {
		return result;
	}
	public void setResult(String result) {
		this.result = result;
	}
}