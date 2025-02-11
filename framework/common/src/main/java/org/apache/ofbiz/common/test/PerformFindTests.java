/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.apache.ofbiz.common.test;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilDateTime;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilValidate;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityListIterator;
import org.apache.ofbiz.service.LocalDispatcher;
import org.apache.ofbiz.service.ServiceUtil;
import org.apache.ofbiz.service.testtools.OFBizTestCase;
import org.apache.ofbiz.entity.util.EntityQuery;

public class PerformFindTests extends OFBizTestCase {

    private static final String MODULE = PerformFindTests.class.getName();
    public PerformFindTests(String name) {
        super(name);
    }

    private static List<GenericValue> getCompleteList(Map<String, Object> context) {
        List<GenericValue> foundElements = new LinkedList<>();
        try (EntityListIterator listIt = (EntityListIterator) context.get("listIt")) {
            if (listIt != null) {
                foundElements = listIt.getCompleteList();
            }
        } catch (GenericEntityException e) {
            Debug.logError(" Failed to extract values from EntityListIterator after a performFind service", MODULE);
        }
        return foundElements;
    }

    private void prepareData() throws Exception {
        Delegator delegator = getDelegator();
        if (EntityQuery.use(delegator).from("TestingType").where("testingTypeId", "PERFOMFINDTEST").cache().queryOne() == null) {
            delegator.create("TestingType", "testingTypeId", "PERFOMFINDTEST");
            delegator.create("TestingType", "testingTypeId", "PERFOMFINDGROUPTEST");
            createTestingValue(delegator, "PERF_TEST_1", 1, "PERFOMFINDTEST", "nice name one", "nice description");
            createTestingValue(delegator, "PERF_TEST_2", 1, "PERFOMFINDTEST", "nice other name two", "bad description");
            createTestingValue(delegator, "PERF_TEST_3", 1, "PERFOMFINDTEST", "medium name three", "medium description");
            createTestingValue(delegator, "PERF_TEST_4", 1, "PERFOMFINDTEST", "bad nme four", null);
            createTestingValue(delegator, "PERF_TEST_5", 1, "PERFOMFINDTEST", "nice name one", null);
            createTestingValue(delegator, "PERF_TEST_6", 1, "PERFOMFINDTEST", null, null);
            createTestingValue(delegator, "PERF_TEST_7", 1, "PERFOMFINDTEST", null, null);
            createTestingValue(delegator, "PERF_TEST_8", 1, "PERFOMFINDTEST", null, null);
            createTestingValue(delegator, "PERF_TEST_9", 2, "PERFOMFINDTEST", "nice name bad nine", "nice description");
            createTestingValue(delegator, "PERF_TEST_10", 1, "PERFOMFINDGROUPTEST", null, null);

            Timestamp nowTimestamp = UtilDateTime.nowTimestamp();
            delegator.create("TestingNode", "testingNodeId", "NODE_1", "description", "Date Node");
            delegator.create("TestingNodeMember", "testingNodeId", "NODE_1", "testingId", "PERF_TEST_5",
                    "fromDate", UtilDateTime.addDaysToTimestamp(nowTimestamp, 1d),
                    "thruDate", UtilDateTime.addDaysToTimestamp(nowTimestamp, 3d),
                    "extendFromDate", UtilDateTime.addDaysToTimestamp(nowTimestamp, -1d),
                    "extendThruDate", UtilDateTime.addDaysToTimestamp(nowTimestamp, 3d));
            delegator.create("TestingNodeMember", "testingNodeId", "NODE_1", "testingId", "PERF_TEST_6",
                    "fromDate", UtilDateTime.addDaysToTimestamp(nowTimestamp, -1d),
                    "thruDate", UtilDateTime.addDaysToTimestamp(nowTimestamp, 1d),
                    "extendFromDate", UtilDateTime.addDaysToTimestamp(nowTimestamp, -1d),
                    "extendThruDate", UtilDateTime.addDaysToTimestamp(nowTimestamp, 3d));
            delegator.create("TestingNodeMember", "testingNodeId", "NODE_1", "testingId", "PERF_TEST_7",
                    "fromDate", UtilDateTime.addDaysToTimestamp(nowTimestamp, -1d),
                    "thruDate", UtilDateTime.addDaysToTimestamp(nowTimestamp, 1d),
                    "extendFromDate", UtilDateTime.addDaysToTimestamp(nowTimestamp, -1d),
                    "extendThruDate", UtilDateTime.addDaysToTimestamp(nowTimestamp, 3d));
            delegator.create("TestingNodeMember", "testingNodeId", "NODE_1", "testingId", "PERF_TEST_8",
                    "fromDate", UtilDateTime.addDaysToTimestamp(nowTimestamp, -3d),
                    "thruDate", UtilDateTime.addDaysToTimestamp(nowTimestamp, 1d),
                    "extendFromDate", UtilDateTime.addDaysToTimestamp(nowTimestamp, -1d),
                    "extendThruDate", UtilDateTime.addDaysToTimestamp(nowTimestamp, 3d));
            delegator.create("TestingNodeMember", "testingNodeId", "NODE_1", "testingId", "PERF_TEST_9",
                    "fromDate", UtilDateTime.addDaysToTimestamp(nowTimestamp, -3d),
                    "thruDate", UtilDateTime.addDaysToTimestamp(nowTimestamp, -1d),
                    "extendFromDate", UtilDateTime.addDaysToTimestamp(nowTimestamp, -1d),
                    "extendThruDate", UtilDateTime.addDaysToTimestamp(nowTimestamp, -3d));
        }
    }

    private static void createTestingValue(Delegator delegator, String testingId, int testingSize,
                                           String testingTypeId, String testingName, String description) throws GenericEntityException {
        delegator.create("Testing",
                "testingId", testingId,
                "testingSize", testingSize,
                "testingTypeId", testingTypeId,
                "testingName", testingName,
                "description", description);
    }

    /**
     * Main test function to call other test.
     * If each function call by junit, it generates a random error with DBCP
     * See the issue OFBIZ-6218 Unit tests throw exception in DBCP for more details
     * @throws Exception
     */
    public void testPerformFind() throws Exception {
        performFindConditionFieldEquals();
        performFindConditionFieldLike();
        performFindConditionDistinct();
        performFindFilterByDate();
        performFindGroup();
        performFindGroupOrAnd();
        performFindDateFindAndIgnoreCase();
        performFindFilterByDateWithDedicateDateField();
    }

    private void performFindConditionFieldEquals() throws Exception {
        GenericValue userLogin = getUserLogin("system");
        prepareData();

        LocalDispatcher dispatcher = getDispatcher();
        Map<String, Object> inputFields = new HashMap<>();
        //first test without condition
        Map<String, Object> performFindMap = UtilMisc.toMap("userLogin", userLogin, "entityName", "Testing", "inputFields", inputFields);
        Map<String, Object> result = dispatcher.runSync("performFind", performFindMap);
        assertTrue(ServiceUtil.isSuccess(result));
        List<GenericValue> foundElements = getCompleteList(result);
        assertTrue("performFind search without condition ", UtilValidate.isEmpty(foundElements));

        //second test without condition and noConditionFind to Y
        performFindMap = UtilMisc.toMap("userLogin", userLogin, "entityName", "Testing", "inputFields", inputFields, "noConditionFind", "Y");
        result = dispatcher.runSync("performFind", performFindMap);
        assertTrue(ServiceUtil.isSuccess(result));
        foundElements = getCompleteList(result);
        assertEquals("performFind search without condition with noConditionFind Y", 10, foundElements.size());

        //third test with equals condition on testingTypeId
        inputFields = UtilMisc.toMap("testingTypeId", "PERFOMFINDTEST");
        performFindMap = UtilMisc.toMap("userLogin", userLogin, "entityName", "Testing", "inputFields", inputFields);
        result = dispatcher.runSync("performFind", performFindMap);
        assertTrue(ServiceUtil.isSuccess(result));
        foundElements = getCompleteList(result);
        List<GenericValue> testingElements = EntityQuery.use(getDelegator())
                .from("Testing")
                .where("testingTypeId", "PERFOMFINDTEST")
                .queryList();
        assertEquals("performFind search without condition with equals on testingTypeId", testingElements.size(), foundElements.size());

        //fourth test with equals condition on testingId
        inputFields = UtilMisc.toMap("testingId", "PERF_TEST_1");
        performFindMap = UtilMisc.toMap("userLogin", userLogin, "entityName", "Testing", "inputFields", inputFields);
        result = dispatcher.runSync("performFind", performFindMap);
        assertTrue(ServiceUtil.isSuccess(result));
        foundElements = getCompleteList(result);
        assertEquals("performFind search without condition with equals on testingId", 1, foundElements.size());
    }

    private void performFindConditionFieldLike() throws Exception {
        GenericValue userLogin = getUserLogin("system");
        prepareData();

        LocalDispatcher dispatcher = getDispatcher();
        //first test like condition
        Map<String, Object> inputFields = UtilMisc.toMap("testingName", "nice", "testingName_op", "like");
        Map<String, Object> performFindMap = UtilMisc.toMap("userLogin", userLogin, "entityName", "Testing", "inputFields", inputFields);
        Map<String, Object> result = dispatcher.runSync("performFind", performFindMap);
        assertTrue(ServiceUtil.isSuccess(result));
        List<GenericValue> foundElements = getCompleteList(result);
        assertEquals("performFind search with like nice% condition", 4, foundElements.size());

        //second test contains condition
        inputFields = UtilMisc.toMap("testingName", "name", "testingName_op", "contains");
        performFindMap = UtilMisc.toMap("userLogin", userLogin, "entityName", "Testing", "inputFields", inputFields);
        result = dispatcher.runSync("performFind", performFindMap);
        assertTrue(ServiceUtil.isSuccess(result));
        foundElements = getCompleteList(result);
        assertEquals("performFind search with like %name% condition", 5, foundElements.size());

        //third test not-like condition
        inputFields = UtilMisc.toMap("testingName", "bad", "testingName_op", "not-like");
        performFindMap = UtilMisc.toMap("userLogin", userLogin, "entityName", "Testing", "inputFields", inputFields);
        result = dispatcher.runSync("performFind", performFindMap);
        assertTrue(ServiceUtil.isSuccess(result));
        foundElements = getCompleteList(result);
        assertEquals("performFind search with not like bad% condition", 5, foundElements.size());

        //fourth test not-contains condition
        inputFields = UtilMisc.toMap("testingName", "name", "testingName_op", "not-contains");
        performFindMap = UtilMisc.toMap("userLogin", userLogin, "entityName", "Testing", "inputFields", inputFields);
        result = dispatcher.runSync("performFind", performFindMap);
        assertTrue(ServiceUtil.isSuccess(result));
        foundElements = getCompleteList(result);
        assertEquals("performFind search with not like %name% condition", 1, foundElements.size());
    }

    private void performFindConditionDistinct() throws Exception {
        GenericValue userLogin = getUserLogin("system");
        prepareData();

        LocalDispatcher dispatcher = getDispatcher();
        //first test without distinct condition
        Map<String, Object> inputFields = UtilMisc.toMap("testingTypeId", "PERFOMFINDTEST");
        List<String> fieldList = UtilMisc.toList("testingName", "testingTypeId");
        Map<String, Object> performFindMap = UtilMisc.toMap("userLogin", userLogin, "entityName", "Testing", "inputFields",
                inputFields, "fieldList", fieldList, "distinct", "N");
        Map<String, Object> result = dispatcher.runSync("performFind", performFindMap);
        assertTrue(ServiceUtil.isSuccess(result));
        List<GenericValue> foundElements = getCompleteList(result);
        assertEquals("performFind search with distinct N", 9, foundElements.size());

        //second test with distinct condition
        performFindMap = UtilMisc.toMap("userLogin", userLogin, "entityName", "Testing", "inputFields", inputFields,
                "fieldList", fieldList, "distinct", "Y");
        result = dispatcher.runSync("performFind", performFindMap);
        assertTrue(ServiceUtil.isSuccess(result));
        foundElements = getCompleteList(result);
        assertEquals("performFind search with distinct Y", 6, foundElements.size());
    }

    private void performFindFilterByDate() throws Exception {
        GenericValue userLogin = getUserLogin("system");
        prepareData();

        LocalDispatcher dispatcher = getDispatcher();
        //first test without filterDate condition
        Map<String, Object> inputFields = UtilMisc.toMap("testingNodeId", "NODE_1");
        Map<String, Object> performFindMap = UtilMisc.toMap("userLogin", userLogin, "entityName", "TestingNodeMember",
                "inputFields", inputFields, "filterByDate", "N", "filterByDateValue", UtilDateTime.nowTimestamp());
        Map<String, Object> result = dispatcher.runSync("performFind", performFindMap);
        assertTrue(ServiceUtil.isSuccess(result));
        List<GenericValue> foundElements = getCompleteList(result);
        assertEquals("performFind search with filterDate N", 5, foundElements.size());

        //second test with filterDate condition
        performFindMap = UtilMisc.toMap("userLogin", userLogin, "entityName", "TestingNodeMember", "inputFields",
                inputFields, "filterByDate", "Y", "filterByDateValue", UtilDateTime.nowTimestamp());
        result = dispatcher.runSync("performFind", performFindMap);
        assertTrue(ServiceUtil.isSuccess(result));
        foundElements = getCompleteList(result);
        assertEquals("performFind search with filterDate Y", 3, foundElements.size());
    }

    /*
     * Find all Testing value with
     * both `nice` in name and description,
     * or with type `PERFOMFINDGROUPTEST`,
     * and that size is `1`.
     */
    private void performFindGroup() throws Exception {
        prepareData();
        LocalDispatcher dispatcher = getDispatcher();
        GenericValue userLogin = getUserLogin("system");

        Map<String, Object> inputFields = UtilMisc.toMap(
                "testingName", "nice", "testingName_grp", "1", "testingName_op", "contains",
                "description", "nice", "description_grp", "1", "description_op", "contains",
                "testingTypeId", "PERFOMFINDGROUPTEST", "testingTypeId_grp", "2",
                "testingSize", 1);

        Map<String, Object> performFindMap = UtilMisc.toMap("userLogin", userLogin, "entityName", "Testing", "inputFields", inputFields);
        Map<String, Object> result = dispatcher.runSync("performFind", performFindMap);
        assertTrue(ServiceUtil.isSuccess(result));
        List<GenericValue> foundElements = getCompleteList(result);
        assertEquals("performFind search with group condition", 2, foundElements.size());
        assertTrue(foundElements.stream().allMatch(genericValue ->
                List.of("PERF_TEST_1", "PERF_TEST_10").contains(genericValue.getString("testingId"))));
    }

    /*
     * Find all Testing value with `nice` in name or description
     * and that are type `PERFOMFINDTEST`
     * and that size is `1`.
     */
    private void performFindGroupOrAnd() throws Exception {
        prepareData();
        LocalDispatcher dispatcher = getDispatcher();
        GenericValue userLogin = getUserLogin("system");
        Map<String, Object> inputFields = UtilMisc.toMap(
                "testingName", "nice", "testingName_grp", "1", "testingName_op", "contains",
                "description", "nice", "description_grp", "1", "description_op", "contains",
                "testingTypeId", "PERFOMFINDTEST", "testingTypeId_grp", "2",
                "testingSize", 1);

        Map<String, Object> performFindMap = UtilMisc.toMap("userLogin", userLogin, "entityName", "Testing",
                "inputFields", inputFields, "groupConditionOperator", "OR_AND");
        Map<String, Object> result = dispatcher.runSync("performFind", performFindMap);
        assertTrue(ServiceUtil.isSuccess(result));
        List<GenericValue> foundElements = getCompleteList(result);
        assertEquals("performFind search with group condition", 3, foundElements.size());
    }

    private void performFindDateFindAndIgnoreCase() throws Exception {
        LocalDispatcher dispatcher = getDispatcher();
        GenericValue userLogin = getUserLogin("system");
        Timestamp now = UtilDateTime.nowTimestamp();

        Map<String, Object> inputFields = UtilMisc.toMap(
                "testingNodeId", "node_1",
                "testingNodeId_ic", "Y",
                "fromDate_fld0_value", UtilDateTime.addDaysToTimestamp(now, -2d),
                "fromDate_fld0_op", "greaterThanEqualTo",
                "fromDate_fld1_value", now,
                "fromDate_fld1_op", "upToDay");

        Map<String, Object> performFindMap = UtilMisc.toMap("userLogin", userLogin, "entityName", "TestingNodeMember", "inputFields", inputFields);
        Map<String, Object> result = dispatcher.runSync("performFind", performFindMap);
        assertTrue(ServiceUtil.isSuccess(result));
        List<GenericValue> foundElements = getCompleteList(result);
        assertEquals("performFind search with date-find widget condition", 2, foundElements.size());
    }

    private void performFindFilterByDateWithDedicateDateField() throws Exception {
        GenericValue userLogin = getUserLogin("system");
        prepareData();

        LocalDispatcher dispatcher = getDispatcher();
        //first test without filterDate condition
        Map<String, Object> inputFields = UtilMisc.toMap("testingNodeId", "NODE_1");
        Map<String, Object> performFindMap = UtilMisc.toMap("userLogin", userLogin, "entityName", "TestingNodeMember", "inputFields", inputFields,
                "filterByDate", "N", "filterByDateValue", UtilDateTime.nowTimestamp(),
                "fromDateName", "extendFromDate", "thruDateName", "extendThruDate");
        Map<String, Object> result = dispatcher.runSync("performFind", performFindMap);
        assertTrue(ServiceUtil.isSuccess(result));
        List<GenericValue> foundElements = getCompleteList(result);
        assertEquals("performFind search with filterDate N and specific date field name", 5, foundElements.size());

        //second test with filterDate condition
        performFindMap = UtilMisc.toMap("userLogin", userLogin, "entityName", "TestingNodeMember", "inputFields", inputFields,
                "filterByDate", "Y", "filterByDateValue", UtilDateTime.nowTimestamp(),
                "fromDateName", "extendFromDate", "thruDateName", "extendThruDate");
        result = dispatcher.runSync("performFind", performFindMap);
        assertTrue(ServiceUtil.isSuccess(result));
        foundElements = getCompleteList(result);
        assertEquals("performFind search with filterDate Y and specific date field name", 4, foundElements.size());
    }
}
