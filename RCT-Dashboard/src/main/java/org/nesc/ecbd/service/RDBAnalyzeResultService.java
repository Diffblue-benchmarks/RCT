package org.nesc.ecbd.service;

import java.util.*;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import org.nesc.ecbd.cache.AppCache;
import org.nesc.ecbd.entity.RDBAnalyze;
import org.nesc.ecbd.entity.RDBAnalyzeResult;
import org.nesc.ecbd.entity.ReportData;
import org.nesc.ecbd.mapper.RDBAnalyzeResultMapper;
import org.nesc.ecbd.report.IAnalyzeDataConverse;
import org.nesc.ecbd.report.converseFactory.ReportDataConverseFacotry;
import org.nesc.ecbd.util.ListSortUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
/**
 * 
 * @author Jacob Cao
 *
 * 2019年1月17日
 */
@Service
public class RDBAnalyzeResultService {
	@Autowired
	RDBAnalyzeResultMapper rdbAnalyzeResultMapper;

	private static final Logger LOG = LoggerFactory.getLogger(RDBAnalyzeResultService.class);

	static final int totalCount = 7;
	
	public void delete(Long id) {
		rdbAnalyzeResultMapper.deleteById(id);
	}

	public void add(RDBAnalyzeResult rdbAnalyzeResult) {
		int count = rdbAnalyzeResultMapper.selectCount(null);
		if(count >= totalCount) {
			rdbAnalyzeResultMapper.deleteOld();
		} 
		rdbAnalyzeResultMapper.insert(rdbAnalyzeResult);
		
	}

	public List<RDBAnalyzeResult> selectList() {
		List<RDBAnalyzeResult> resList = rdbAnalyzeResultMapper.selectList(null);
		return resList;
	}

	/**
	 * get result list by redisInfoId
	 * @param redisInfoId redisInfoId
	 * @return List<RDBAnalyzeResult>
	 */
	public List<RDBAnalyzeResult> selectAllResultById(Long redisInfoId) {
		if(null == redisInfoId) {
			return null;
		}
		List<RDBAnalyzeResult> results = null;
		try {
			results = rdbAnalyzeResultMapper.selectAllResultById(redisInfoId);
		} catch (Exception e) {
			LOG.error("selectAllResultById failed!", e);
		}
		return results;
	}

	/**
	 * get result list by schedule_id
	 * @param redisInfoId schedule_id
	 * @return List<RDBAnalyzeResult>
	 */
	public List<RDBAnalyzeResult> selectAllResultByIdExceptLatest(Long redisInfoId) {
		if(null == redisInfoId) {
			return null;
		}
		List<RDBAnalyzeResult> results = null;
		try {
			results = rdbAnalyzeResultMapper.selectAllResultByIdExceptLatest(redisInfoId);
		} catch (Exception e) {
			LOG.error("selectAllResultById failed!", e);
		}
		return results;
	}

	/**
	 * get result by redisInfoId
	 * @param redisInfoId redisInfoId
	 * @return RDBAnalyzeResult 不包含 redisInfoId 和 scheduleId
	 */
	public RDBAnalyzeResult selectLatestResultByRID(Long redisInfoId) {
		if(null == redisInfoId) {
			return null;
		}
		RDBAnalyzeResult result = null;
		try {
			result = rdbAnalyzeResultMapper.selectLatestResultByRedisInfoId(redisInfoId);
		} catch (Exception e) {
			LOG.error("selectLatestResultByRedisInfoId failed!", e);
		}
		return result;
	}

	/**
	 * get result by RID AND SID
	 * @param redisInfoId redisInfoId
	 * @param scheduleId scheduleId
	 * @return RDBAnalyzeResult
	 */
	public RDBAnalyzeResult selectResultByRIDandSID(Long redisInfoId, Long scheduleId) {
		if(null == redisInfoId || null == scheduleId) {
			return null;
		}
		RDBAnalyzeResult result = null;
		try {
			result = rdbAnalyzeResultMapper.selectByRedisIdAndSId(redisInfoId, scheduleId);
		} catch (Exception e) {
			LOG.error("selectLatestResultByRedisInfoId failed!", e);
		}
		return result;
	}

	public List<RDBAnalyzeResult> selectByMap(Map<String, Object> map) {
		return rdbAnalyzeResultMapper.selectByMap(map);
	}

	public boolean insert(RDBAnalyzeResult rdbAnalyzeResult){
		try {
			return checkResult(rdbAnalyzeResultMapper.insert(rdbAnalyzeResult));
		} catch (Exception e) {
			LOG.error("selectLatestResultByRedisInfoId failed!", e);
		}
		return false;
	}

	public boolean checkResult(int result) {
		if (result > 0) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * 插入数据库，并将结果返回
	 * @param rdbAnalyze
	 * @param data
	 * @return
	 */
	public RDBAnalyzeResult reportDataWriteToDb(RDBAnalyze rdbAnalyze, Map<String, Set<String>> data) {
		try {
			Long scheduleId = AppCache.scheduleProcess.get(rdbAnalyze.getId());
			Long redisInfoId = rdbAnalyze.getPid();
			IAnalyzeDataConverse analyzeDataConverse = null;
			Map<String, String> dbResult = new HashMap<>();
			for(Map.Entry<String, Set<String>> entry : data.entrySet()) {
				analyzeDataConverse = ReportDataConverseFacotry.getReportDataConverse(entry.getKey());
				if(null != analyzeDataConverse) {
					dbResult.putAll(analyzeDataConverse.getMapJsonString(entry.getValue()));

				}
			}
			String result = JSON.toJSONString(dbResult);
			RDBAnalyzeResult rdbAnalyzeResult = new RDBAnalyzeResult();
			rdbAnalyzeResult.setRedisInfoId(redisInfoId);
			rdbAnalyzeResult.setScheduleId(scheduleId);
			rdbAnalyzeResult.setResult(result);
			insert(rdbAnalyzeResult);
			return rdbAnalyzeResult;
		} catch (Exception e) {
			LOG.error("reportDataWriteToDb write to db error!", e);
		}
		return null;
	}


	/**
	 * get list keyPrefix
	 * @param result result
	 * @return List<String> keyPrefix
	 */
	public List<JSONObject> getAllKeyPrefixByResult(String result){
		List<JSONObject> resultJsonObj = new ArrayList<>(500);
		if(null == result || "".equals(result.trim())) {
			return resultJsonObj;
		}
		JSONArray jsonArray = getJSONArrayFromResultByKey(result, IAnalyzeDataConverse.PREFIX_KEY_BY_COUNT);
		if(null == jsonArray) {
			return resultJsonObj;
		}
		JSONObject oneRow;
		JSONObject jsonObject;
		for(Object obj : jsonArray) {
			oneRow = (JSONObject) obj;
			jsonObject = new JSONObject();
			jsonObject.put("value", oneRow.getString("prefixKey"));
			jsonObject.put("label", oneRow.getString("prefixKey"));
			resultJsonObj.add(jsonObject);
		}
		return resultJsonObj;
	}

	/**
	 * gong zhe xian tu shi yong,
	 * @param id
	 * @param scheduleId
	 * @param key
	 * @return
	 */
	public Object getLineStringFromResult(Long id, Long scheduleId, String key) throws Exception{
		if(null == key || "".equals(key.trim())) {
			throw new Exception("key should not null!");
		}
		JSONArray jsonArray = null;
		Map<String, Object> result = new HashMap<>();
		List<String> x = new ArrayList<>();
		List<Long> y = new ArrayList<>();
		JSONObject jsonObject;
		if(IAnalyzeDataConverse.PREFIX_KEY_BY_COUNT.equalsIgnoreCase(key)){
			jsonArray = getJsonArrayFromResult(id, scheduleId, IAnalyzeDataConverse.PREFIX_KEY_BY_COUNT);
			for(Object obj : jsonArray) {
				jsonObject = (JSONObject) obj;
				x.add(jsonObject.getString("prefixKey"));
				y.add(jsonObject.getLong("keyCount"));
			}
		} else if (IAnalyzeDataConverse.PREFIX_KEY_BY_MEMORY.equalsIgnoreCase(key)) {
			jsonArray = getJsonArrayFromResult(id, scheduleId, IAnalyzeDataConverse.PREFIX_KEY_BY_MEMORY);
			for(Object obj : jsonArray) {
				jsonObject = (JSONObject) obj;
				x.add(jsonObject.getString("prefixKey"));
				y.add(jsonObject.getLong("memorySize"));
			}
		} else {
			return "";
		}
		result.put("x", x);
		result.put("y", y);
		return result;
	}

	public JSONArray getJsonArrayFromResult(Long id, Long scheduleId, String key) throws Exception{
		if(null == id) {
			throw new Exception("pid should not null!");
		}
		RDBAnalyzeResult rdbAnalyzeResult;
		if(null == scheduleId) {
			rdbAnalyzeResult = selectLatestResultByRID(id);
		} else {
			rdbAnalyzeResult = selectResultByRIDandSID(id, scheduleId);
		}
		JSONArray result = getJSONArrayFromResultByKey(rdbAnalyzeResult.getResult(), key);
		return result;
	}

	public Object getListStringFromResult(Long id, Long scheduleId, String key) throws Exception{
		return getJsonArrayFromResult(id, scheduleId, key);
	}



	public JSONArray getJSONArrayFromResultByKey(String result, String key) {
		if((null == result) || ("".equals(result.trim())) || (null == key) || ("".equals(key.trim()))) {
			return null;
		}
		JSONObject resultJsonObj = JSONObject.parseObject(result);

		JSONArray jsonArray = JSONObject.parseArray(resultJsonObj.getString(key));
		return jsonArray;
	}

	public Object getTopKeyFromResultByKey(Long id, Long scheduleId, Long key) throws Exception{
		if(null == id || null == key) {
			throw new Exception("id or key should not null!");
		}
		RDBAnalyzeResult rdbAnalyzeResult;
		if (null == scheduleId) {
			rdbAnalyzeResult = selectLatestResultByRID(id);
		} else {
			rdbAnalyzeResult = selectResultByRIDandSID(id, scheduleId);
		}
		JSONArray result = getTopKeyFromResultByKey(rdbAnalyzeResult.getResult(), key);
		return result;
	}

	public JSONArray getTopKeyFromResultByKey(String result, Long startNum) {
		if(null == result || "".equals(result.trim())) {
			return null;
		}
		JSONObject resultJsonObj = JSONObject.parseObject(result);
		JSONObject startNumData = JSONObject.parseObject(resultJsonObj.getString(IAnalyzeDataConverse.TOP_KEY_ANALYZE));
		JSONArray jsonArray = startNumData.getJSONArray(String.valueOf(startNum));
		return jsonArray;
	}

	/**
	 *
	 * @param pid redisInfoID
	 * @param type  PrefixKeyByCount,PrefixKeyByMemory
	 * @return JSONArray
	 */
	public JSONArray getPrefixLineByCountOrMem(Long pid, String type, int top, String prefixKey) {
		String sortColumn = getSortColumn(type);
		RDBAnalyzeResult rdbAnalyzeLatestResult = selectLatestResultByRID(pid);
		if( null == rdbAnalyzeLatestResult) {
			return null;
		}
		JSONArray arrayResult = getJSONArrayFromResultByKey(rdbAnalyzeLatestResult.getResult(), type);
		if(null == arrayResult) {
			return null;
		}
		List<JSONObject> resultObjecList = getJSONObjList(arrayResult);
		ListSortUtil.sortByKeyValueDesc(resultObjecList, sortColumn);
		// top == -1 代表全部，否则截取前 top
		// 需要返回的前缀
		List<String> prefixKeyList = getcolumnKeyList(prefixKey, resultObjecList, "prefixKey", top);
		// except Latest RDBAnalyzeResult
		List<RDBAnalyzeResult> rdbAnalyzeResultList = selectAllResultByIdExceptLatest(pid);
		// key ：prefixKey
		Map<String, Map<String, JSONObject>> resultMap = new HashMap<>(7);
		Map<String, JSONObject> latest = getMapJSONByResult(rdbAnalyzeLatestResult,arrayResult);
		resultMap.put(String.valueOf(rdbAnalyzeLatestResult.getScheduleId()), latest);
		// scheduleList 列表
		List<Long> scheduleList = new ArrayList<>(7);
		scheduleList.add(rdbAnalyzeLatestResult.getScheduleId());
		for(RDBAnalyzeResult rdbAnalyzeResult : rdbAnalyzeResultList) {
			arrayResult = getJSONArrayFromResultByKey(rdbAnalyzeResult.getResult(), type);
			resultMap.put(String.valueOf(rdbAnalyzeResult.getScheduleId()) ,getMapJSONByResult(rdbAnalyzeResult, arrayResult));
			scheduleList.add(rdbAnalyzeResult.getScheduleId());
		}
		Collections.sort(scheduleList);
		JSONArray result = new JSONArray();
		JSONObject arrayJsonObj;
		String id;
		Map<String, JSONObject> res;
		StringBuilder sb;
		JSONObject temp;
		String value;
		for(String prefix : prefixKeyList) {
			arrayJsonObj = new JSONObject();
			sb = new StringBuilder();
			for(Long schedule : scheduleList) {
				id = String.valueOf(schedule);
				res = resultMap.get(id);
				temp = res.get(prefix);
				if(null == temp) {
					// 没有对应前缀，值为 0
					value = "0";
				} else {
					value = temp.getString(sortColumn);
				}
				sb.append(value).append(",");
			}
			arrayJsonObj.put("value", sb.toString().substring(0, sb.toString().length() - 1));
			arrayJsonObj.put("key", prefix);
			result.add(arrayJsonObj);
		}
		return result;

	}

	/**
	 * 将数据中结果转换为 折线图需要的对象
	 * @param rdbAnalyzeResult
	 * @return
	 */
	private  Map<String, JSONObject> getMapJSONByResult(RDBAnalyzeResult rdbAnalyzeResult, JSONArray arrayResult) {
		Map<String, JSONObject> result = new HashMap<>(500);
		Long scheduleId = rdbAnalyzeResult.getScheduleId();
		JSONObject object;
		for(Object obj : arrayResult) {
			object = (JSONObject) obj;
			object.put("scheduleId", scheduleId);
			result.put(object.getString("prefixKey"), object);
		}
		JSONObject scheduleIdJson = new JSONObject();
		scheduleIdJson.put("scheduleId", scheduleId);
		result.put("scheduleId", scheduleIdJson);
		return result;
	}

	/**
	 *
	 * @param prefixKey 固定前缀
	 * @param resultObjecList result
	 * @param columnName 获取的列
	 * @param top top
	 * @return
	 */
	public List<String> getcolumnKeyList(String prefixKey, List<JSONObject> resultObjecList, String columnName, int top) {
		List<String> prefixKeyList = new ArrayList<>(10);
		if(null == prefixKey) {
			if(top == -1) {
				top = resultObjecList.size();
			}
			int i = 0;
			for(JSONObject tempObj : resultObjecList) {
				if(i >= top){
					break;
				}
				prefixKeyList.add(tempObj.getString(columnName));
				i++;
			}
		} else {
			prefixKeyList.add(prefixKey);
		}
		return prefixKeyList;
	}

	/**
	 * 将 jsonArray 转换为 List<JSONObject>
	 * @param jsonArray array
	 * @return List<JSONObject>
	 */
	public List<JSONObject> getJSONObjList(JSONArray jsonArray) {
		List<JSONObject> resultList = new ArrayList<>(300);
		JSONObject jsonObject;
		for(Object obj : jsonArray) {
			jsonObject = (JSONObject) obj;
			resultList.add(jsonObject);
		}
		return resultList;
	}

	public String getSortColumn(String type) {
		String column;
		if(IAnalyzeDataConverse.PREFIX_KEY_BY_COUNT.equalsIgnoreCase(type)) {
			column = "keyCount";
		} else if (IAnalyzeDataConverse.PREFIX_KEY_BY_MEMORY.equalsIgnoreCase(type)) {
			column = "memorySize";
		} else {
			column = null;
		}
		return column;
	}


	public JSONArray getPrefixType(Long id, Long scheduleId) throws Exception {
		if (null == id) {
			throw new Exception("pid should not null!");
		}
		JSONArray count = getJsonArrayFromResult(id, scheduleId, IAnalyzeDataConverse.PREFIX_KEY_BY_COUNT);
		JSONArray memory = getJsonArrayFromResult(id, scheduleId, IAnalyzeDataConverse.PREFIX_KEY_BY_MEMORY);
		if(null == memory || memory.isEmpty()) {
			return count;
		}
		Map<String, JSONObject> memMap = getJsonObject(memory);
		JSONArray result = getPrefixArrayAddMem(count, memMap, "memorySize");
		return result;
	}

	public JSONArray getPrefixArrayAddMem(JSONArray count, Map<String, JSONObject> memMap, String column) {
		JSONArray result = new JSONArray();
		JSONObject jsonObject;
		JSONObject temp;
		String prefix;
		for(Object obj : count) {
			jsonObject = (JSONObject) obj;
			prefix = jsonObject.getString("prefixKey");
			temp = memMap.get(prefix);
			if(null != temp) {
				jsonObject.put(column, temp.getString(column));
			}
			result.add(jsonObject);
		}
		return result;
	}

	public Map<String, JSONObject> getJsonObject(JSONArray jsonArray) {
		Map<String, JSONObject> result = new HashMap<>(280);
		JSONObject temp;
		for(Object obj : jsonArray) {
			temp = (JSONObject) obj;
			result.put(temp.getString("prefixKey"), temp);
		}
		return result;
	}


	/**
	 * 获取上一次的数据转换为ReportData
	 * @param pid pid
	 * @return Map
	 */
	public Map<String, ReportData> getReportDataLatest(Long pid) {
		if(null == pid) {
			return null;
		}
		RDBAnalyzeResult rdbAnalyzeResult = selectLatestResultByRID(pid);
		if( null == rdbAnalyzeResult) {
			return null;
		}
		JSONArray countResult = getJSONArrayFromResultByKey(rdbAnalyzeResult.getResult(), IAnalyzeDataConverse.PREFIX_KEY_BY_COUNT);
		JSONArray memResult = getJSONArrayFromResultByKey(rdbAnalyzeResult.getResult(), IAnalyzeDataConverse.PREFIX_KEY_BY_MEMORY);
		return getPrefixReportData(countResult, memResult);
	}

	/**
	 * 数据转换
	 * @param arrayResult
	 * @return
	 */
	private  Map<String, JSONObject> getMapJsonPrefixByResult(JSONArray arrayResult) {
		Map<String, JSONObject> result = new HashMap<>(500);
		JSONObject object;
		if(null != arrayResult && !arrayResult.isEmpty()) {
			for(Object obj : arrayResult) {
				object = (JSONObject) obj;
				result.put(object.getString("prefixKey"), object);
			}
		}
		return result;
	}

	private Map<String, ReportData> getPrefixReportData(JSONArray countResult, JSONArray memResult) {
		if(null == countResult || countResult.isEmpty() || null == memResult || memResult.isEmpty()) {
			return null;
		}
		Map<String, JSONObject> memResultJsonObj = getMapJsonPrefixByResult(memResult);
		JSONObject temp;
		Map<String, ReportData> result = new HashMap<>(667);
		ReportData reportData;
		String prefix;
		for(Object obj : countResult) {
			temp = (JSONObject)obj;
			prefix = temp.getString("prefixKey");
			reportData = new ReportData();
			reportData.setKey(prefix);
			reportData.setCount(temp.getLongValue("keyCount"));
			JSONObject mem = memResultJsonObj.get(prefix);
			if(null == mem) {
				reportData.setBytes(0L);
			} else
			{
				reportData.setBytes(mem.getLongValue("memorySize"));
			}
			result.put(prefix, reportData);
		}
		return result;

	}

}
