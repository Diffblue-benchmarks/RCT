package org.nesc.ecbd.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nesc.ecbd.cache.AppCache;
import org.nesc.ecbd.common.BaseController;
import org.nesc.ecbd.common.RestResponse;
import org.nesc.ecbd.entity.AnalyzeStatus;
import org.nesc.ecbd.entity.RDBAnalyze;
import org.nesc.ecbd.entity.RDBAnalyzeResult;
import org.nesc.ecbd.entity.RedisInfo;
import org.nesc.ecbd.entity.ScheduleDetail;
import org.nesc.ecbd.service.RDBAnalyzeResultService;
import org.nesc.ecbd.service.RDBAnalyzeService;
import org.nesc.ecbd.service.RDBScheduleJob;
import org.nesc.ecbd.service.RedisInfoService;
import org.nesc.ecbd.service.ScheduleTaskService;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

/**
 * 
 * @author Jacob Cao
 * 
 *         2019年1月17日 rdb 任务配置信息管理
 */
@RestController
@RequestMapping("/rdb")
public class RDBAnalyzeController extends BaseController {

	private static final Logger LOG = LoggerFactory.getLogger(RDBAnalyzeController.class);
	@Autowired
	RDBAnalyzeService rdbAnalyzeService;
	@Autowired
	RDBAnalyzeResultService rdbAnalyzeResultService;

	@Autowired
	RedisInfoService redisInfoService;
	@Autowired
	ScheduleTaskService taskService;

	// update
	@RequestMapping(value = { "", "/" }, method = RequestMethod.PUT)
	@ResponseBody
	public RestResponse updateRdbAnalyze(@RequestBody RDBAnalyze rdbAnalyze) {

		if (rdbAnalyzeService.updateRdbAnalyze(rdbAnalyze)) {
			try {
				taskService.delTask("rdb" + String.valueOf(rdbAnalyze.getId()));
			} catch (SchedulerException e) {
				LOG.error("schedule job delete faild!message:{}", e.getMessage());
			}

			if (rdbAnalyze.getAutoAnalyze()) {
				try {
					taskService.addTask(rdbAnalyze, RDBScheduleJob.class);
				} catch (SchedulerException e) {
					LOG.error("schedule job add faild!message:{}", e.getMessage());
				}
			}
			return SUCCESS("update success!");
		} else {
			return ERROR("update fail!");
		}
	}

	// add
	@RequestMapping(value = { "", "/" }, method = RequestMethod.POST)
	public RestResponse addRdbAnalyze(@RequestBody RDBAnalyze rdbAnalyze) {
		if (rdbAnalyzeService.add(rdbAnalyze)) {
			if (rdbAnalyze.getAutoAnalyze()) {
				try {
					taskService.addTask(rdbAnalyze, RDBScheduleJob.class);
				} catch (SchedulerException e) {
					LOG.error("schedule job add faild!message:{}", e.getMessage());
				}
			}
			return SUCCESS("add success!", rdbAnalyze.getId());
		} else {
			return ERROR("add fail!");
		}
	}

	/**
	 * 取消定时任务
	 * 
	 * @param id
	 */
	@RequestMapping(value = "/cance/{id}", method = RequestMethod.GET)
	public RestResponse canceRdbAnalyze(@PathVariable("id") Long id) {
		try {
			taskService.delTask("rdb" + String.valueOf(id));
		} catch (SchedulerException e) {
			LOG.error("schedule job delete faild!message:{}", e.getMessage());
		}
		return SUCCESS();
	}

	@RequestMapping(value = { "/pid/{pid}" }, method = RequestMethod.GET)
	public RestResponse getRDBAnalyzeByParentID(@PathVariable Long pid) {
		JSONObject data = new JSONObject();
		RDBAnalyze rdbAnalyze = rdbAnalyzeService.getRDBAnalyzeByPid(pid);
		RedisInfo redisInfo;
		if (null == rdbAnalyze) {
			redisInfo = redisInfoService.selectById(pid);
			rdbAnalyze = new RDBAnalyze();
			rdbAnalyze.setRedisInfo(redisInfo);
		}
		if (null != rdbAnalyze) {
			redisInfo = rdbAnalyze.getRedisInfo();
			if (null != redisInfo) {
				rdbAnalyze.setPid(redisInfo.getId());
			}
		}
		data.put("info", rdbAnalyze);
		Long id = rdbAnalyzeService.getRedisIDBasePID(pid);
		if(id!=null) {
			data.put("status", rdbAnalyzeService.ifRDBAnalyzeIsRunning(id));
		}		
		return SUCCESS_DATA(data);
	}

	@GetMapping("/analyze/status/{pid}")
	public RestResponse ifRDBAnalyzeIsRunning(@PathVariable Long pid) {
		Long id = rdbAnalyzeService.getRedisIDBasePID(pid);
		return SUCCESS_DATA(rdbAnalyzeService.ifRDBAnalyzeIsRunning(id));

	}

	/**
	 * 根据id下发分析任务到指定的agent 中
	 * 
	 * @param id
	 * @return {message:"",status:true/false }
	 */

	@RequestMapping(value = "allocation_job/{id}", method = RequestMethod.GET)
	public RestResponse allocationJob(@PathVariable("id") Long id) {
		RDBAnalyze rdbAnalyze = rdbAnalyzeService.getRDBAnalyzeById(id);
		int[] result = null;
		if (rdbAnalyze.getAnalyzer().contains(",")) {
			String[] str = rdbAnalyze.getAnalyzer().split(",");
			result = new int[str.length];
			for (int i = 0; i < str.length; i++) {
				result[i] = Integer.parseInt(str[i]);

			}
		} else {
			result = new int[1];
			result[0] = Integer.parseInt(rdbAnalyze.getAnalyzer());
		}
		JSONObject responseResult = rdbAnalyzeService.allocationRDBAnalyzeJob(id, result);
		return SUCCESS_DATA(responseResult);
	}

	/**
	 * 根据ID查询调度进程
	 *
	 */
	@RequestMapping(value = "schedule_detail/{id}", method = RequestMethod.GET)
	public RestResponse scheduleDetail(@PathVariable("id") Long rdbAnalyzeID) {
		RDBAnalyze  rdbAnalyze = rdbAnalyzeService.getRDBAnalyzeByPid(rdbAnalyzeID);
		List<ScheduleDetail> scheduleDetail = AppCache.scheduleDetailMap.get(rdbAnalyze.getId());
		List<ScheduleDetail> result = new ArrayList<ScheduleDetail>();
		if (scheduleDetail != null && scheduleDetail.size() > 0) {
			for (ScheduleDetail scheduleDetails : scheduleDetail) {
				AnalyzeStatus stautStatus = scheduleDetails.getStatus();
				if ("DONE".equalsIgnoreCase(stautStatus.name())) {
					scheduleDetails.setProcess(100);
				} else {
					float ratio = 0;
					if (AppCache.keyCountMap.containsKey(scheduleDetails.getInstance())) {
						float keyCount = AppCache.keyCountMap.get(scheduleDetails.getInstance());
						ratio = scheduleDetails.getProcess() / keyCount;
					}
					scheduleDetails.setProcess((int) (ratio * 100));					
				}

				result.add(scheduleDetails);
			}
		}
		Collections.sort(result, new Comparator<ScheduleDetail>() {
			@Override
			public int compare(ScheduleDetail o1, ScheduleDetail o2) {
				if ("DONE".equals(o1.getStatus().name()) || "DONE".equals(o2.getStatus().name()))
					return o1.getProcess() - o2.getProcess();
				else {
					return o1.getInstance().compareTo(o2.getInstance());
				}
			}
		});
		return SUCCESS_DATA(result);
	}

	/**
	 * 取消分析任务
	 * 
	 * @param instance (pid)
	 * @return JSON string: </br>
	 *         <code>
	 *         cancel succeed:
	 *            {
	 *                "canceled": true,
	 *                "lastStatus": "[AnalyzeStatus]",
	 *                "currentStatus": "[AnalyzeStatus]"
	 *            }
	 *         cancel failed:
	 *            {
	 *                "canceled": false,
	 *                "lastStatus": "[AnalyzeStatus]",
	 *                "currentStatus": "[AnalyzeStatus]"
	 *            }
	 *         </code>
	 */
	@RequestMapping(value = "cance_job/{instance}", method = RequestMethod.GET)
	public RestResponse canceJob(@PathVariable String instance) {
		JSONObject result = rdbAnalyzeService.canceRDBAnalyze(instance);
		return SUCCESS_DATA(result);
	}

	/**
	 * get schedule_id list
	 * 
	 * @param pid pid
	 * @return schedule_id List
	 */
	@GetMapping("/all/schedule_id")
	public RestResponse getAllScheduleId(@RequestParam Long pid) {
		try {
			// Long id = rdbAnalyzeService.getRedisIDBasePID(pid);
			if (null == pid) {
				return ERROR("pid  not null!");
			}
			Map<String, Object> queryMap = new HashMap<>(2);
			queryMap.put("redis_info_id", pid);
			List<RDBAnalyzeResult> rdbAnalyzeResultList = rdbAnalyzeResultService.selectByMap(queryMap);
			List<JSONObject> result = new ArrayList<>(500);
			JSONObject obj;
			for (RDBAnalyzeResult rdbAnalyzeResult : rdbAnalyzeResultList) {
				obj = new JSONObject();
				obj.put("value", String.valueOf(rdbAnalyzeResult.getScheduleId()));
				obj.put("label", String.valueOf(rdbAnalyzeResult.getScheduleId()));
				result.add(obj);
			}
			return SUCCESS_DATA(result);
		} catch (Exception e) {
			LOG.error("getAllScheduleId failed!", e);
			return ERROR("getAllScheduleId failed!");
		}
	}

	/**
	 * get all key_prefix
	 * 
	 * @param pid
	 * @param scheduleId
	 * @return
	 */
	@GetMapping("/all/key_prefix")
	public RestResponse getAllKeyPrefix(@RequestParam Long pid, @RequestParam(value = "scheduleId", required = false) Long scheduleId) {
		try {
			if (null == pid) {
				return ERROR("pid should not null!");
			}
			RDBAnalyzeResult rdbAnalyzeResult;
			if(null != scheduleId){
				rdbAnalyzeResult = rdbAnalyzeResultService.selectResultByRIDandSID(pid, scheduleId);
			} else {
				rdbAnalyzeResult = rdbAnalyzeResultService.selectLatestResultByRID(pid);
			}
			if(null == rdbAnalyzeResult) {
				return SUCCESS_DATA(null);
			}
			return SUCCESS_DATA(rdbAnalyzeResultService.getAllKeyPrefixByResult(rdbAnalyzeResult.getResult()));
		} catch (Exception e) {
			LOG.error("getAllKey_prefix failed!", e);
			return ERROR("getAllKey_prefix failed!");
		}
	}

	/**
	 * get cheat data
	 * 
	 * @param type       value:
	 *                   PrefixKeyByCount,PrefixKeyByMemory,DataTypeAnalyze,TTLAnalyze
	 * @param pid
	 * @param scheduleId
	 * @return
	 */
	@GetMapping("/chart/{type}")
	public RestResponse getChartDataByType(@PathVariable("type") String type, @RequestParam Long pid,
			@RequestParam(value = "scheduleId", required = false) Long scheduleId) {
		try {
			return SUCCESS_DATA(rdbAnalyzeResultService.getListStringFromResult(pid, scheduleId, type));
		} catch (Exception e) {
			LOG.error("getChartDataByType failed!", e);
			return ERROR("getChartDataByType failed!");
		}
	}
	/**
	 * get table data
	 *
	 * @param pid
	 * @param scheduleId
	 * @return
	 */
	@GetMapping("/table/prefix")
	public RestResponse getPrefixType(@RequestParam Long pid, @RequestParam(value = "scheduleId", required = false) Long scheduleId) {
		try {
			return SUCCESS_DATA(rdbAnalyzeResultService.getPrefixType(pid, scheduleId));
		}
		catch (Exception e) {
			LOG.error("getPrefixType failed!", e);
			return ERROR("getPrefixType failed!");
		}
	}


	/**
	 * getTopKey data
	 * 
	 * @param pid
	 * @param scheduleId
	 * @param type       0:string 5:hash 10:list 15:set
	 * @return
	 */
	@GetMapping("/top_key")
	public RestResponse getPrefixKeyByMem(@RequestParam Long pid, @RequestParam(value = "scheduleId", required = false) Long scheduleId,
			@RequestParam Long type) {
		try {
			return SUCCESS_DATA(rdbAnalyzeResultService.getTopKeyFromResultByKey(pid, scheduleId, type));
		} catch (Exception e) {
			LOG.error("getPrefixKeyByMem failed!", e);
			return ERROR("getPrefixKeyByMem failed!");
		}

	}

	/**
	 * 折线图
	 * 
	 * @param type       PrefixKeyByCount,PrefixKeyByMemory
	 * @param pid
	 * @param scheduleId
	 * @return
	 */
	@GetMapping("/line/{type}")
	public RestResponse getPerfixLine(@PathVariable("type") String type, @RequestParam Long pid,
			@RequestParam(value = "scheduleId", required = false) Long scheduleId) {
		try {
			// Long id = rdbAnalyzeService.getRedisIDBasePID(pid);
			return SUCCESS_DATA(rdbAnalyzeResultService.getLineStringFromResult(pid, scheduleId, type));
		} catch (Exception e) {
			LOG.error("getPerfixLine failed!", e);
			return ERROR("getPerfixLine failed!");
		}
	}

	/**
	 *
	 * @param type PrefixKeyByCount,PrefixKeyByMemory
	 * @param pid redisInfoID
	 * @return JSONArray
	 */
	@GetMapping("/line/prefix/{type}")
	public RestResponse getPrefixLineByCountOrMem(@PathVariable String type, @RequestParam Long pid, @RequestParam(value = "prefixKey", required = false) String prefixKey) {
		try {
			JSONArray result = rdbAnalyzeResultService.getPrefixLineByCountOrMem(pid, type, 20, prefixKey);
			return SUCCESS_DATA(result);
		}
		catch (Exception e) {
			LOG.error("getPrefixLineByCountOrMem failed!", e);
			return ERROR("getPrefixLineByCountOrMem failed!");
		}
	}



}
