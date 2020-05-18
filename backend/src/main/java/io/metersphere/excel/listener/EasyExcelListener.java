package io.metersphere.excel.listener;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelAnalysisException;
import com.alibaba.excel.util.StringUtils;
import io.metersphere.commons.utils.LogUtil;
import io.metersphere.excel.utils.ExcelValidateHelper;
import io.metersphere.excel.domain.ExcelErrData;
import io.metersphere.i18n.Translator;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

@Component
public abstract class EasyExcelListener <T> extends AnalysisEventListener<T> {

    protected List<ExcelErrData<T>> errList = new ArrayList<>();

    protected List<T> list = new ArrayList<>();

    /**
     * 每隔2000条存储数据库，然后清理list ，方便内存回收
     */
    protected static final int BATCH_COUNT = 2000;

    protected Class<T> clazz;

    @Resource
    ExcelValidateHelper excelValidateHelper;

    public EasyExcelListener(){
        Type type = getClass().getGenericSuperclass();
        this.clazz = (Class<T>) ((ParameterizedType) type).getActualTypeArguments()[0];
    }

    /**
     * 这个每一条数据解析都会来调用
     *
     * @param t
     * @param analysisContext
     */
    @Override
    public void invoke(T t, AnalysisContext analysisContext) {
        String errMsg;
        Integer rowIndex = analysisContext.readRowHolder().getRowIndex();
        try {
            //根据excel数据实体中的javax.validation + 正则表达式来校验excel数据
            errMsg = excelValidateHelper.validateEntity(t);
            //自定义校验规则
            errMsg = validate(t, errMsg);
        } catch (NoSuchFieldException e) {
            errMsg = Translator.get("parse_data_error");
            LogUtil.error(e.getMessage(), e);
        }

        if (!StringUtils.isEmpty(errMsg)) {
            ExcelErrData excelErrData = new ExcelErrData(t, rowIndex,
                    Translator.get("number") + rowIndex + Translator.get("row") + Translator.get("erroer")
                            + "：" + errMsg);
            errList.add(excelErrData);
        } else {
            list.add(t);
        }

        if (list.size() > BATCH_COUNT) {
            saveData();
            list.clear();
        }
    }

    /**
     * 可重写该方法
     * 自定义校验规则
     * @param data
     * @param errMsg
     * @return
     */
    public String validate(T data, String errMsg) {
        return errMsg;
    }

    /**
     * 自定义数据保存操作
     */
    public abstract void saveData();

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        saveData();
        list.clear();
    }


    /**
      * 校验excel头部
      * @param headMap 传入excel的头部（第一行数据）数据的index,name
      * @param context
      */
    @Override
    public void invokeHeadMap(Map<Integer, String> headMap, AnalysisContext context) {
        super.invokeHeadMap(headMap, context);
        if (clazz != null){
            try {
                Set<String> fieldNameSet = getFieldNameSet(clazz);
                Collection<String> values = headMap.values();
                for (String key : fieldNameSet) {
                    if (!values.contains(key)){
                        throw new ExcelAnalysisException(Translator.get("missing_header_information") + ":" + key);
                    }
                }
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
        }
    }

    /**
      * @description: 获取注解里ExcelProperty的value
     */
    public Set<String> getFieldNameSet(Class clazz) throws NoSuchFieldException {
        Set<String> result = new HashSet<>();
        Field field;
        Field[] fields = clazz.getDeclaredFields();
        for (int i = 0; i < fields.length ; i++) {
            field = clazz.getDeclaredField(fields[i].getName());
            field.setAccessible(true);
            ExcelProperty excelProperty = field.getAnnotation(ExcelProperty.class);
            if(excelProperty != null){
                StringBuilder value = new StringBuilder();
                for (String v : excelProperty.value()) {
                    value.append(v);
                }
                result.add(value.toString());
            }
        }
        return result;
    }


    public List<ExcelErrData<T>> getAndClearErrList() {
        List<ExcelErrData<T>> tmp = this.errList;
        this.errList = new ArrayList<>();
        return tmp;
    }

}