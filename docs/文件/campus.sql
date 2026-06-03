/*
 Navicat Premium Dump SQL

 Source Server         : local
 Source Server Type    : MySQL
 Source Server Version : 80045 (8.0.45)
 Source Host           : localhost:3306
 Source Schema         : campus

 Target Server Type    : MySQL
 Target Server Version : 80045 (8.0.45)
 File Encoding         : 65001

 Date: 04/05/2026 13:29:28
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for academic_calendar
-- ----------------------------
DROP TABLE IF EXISTS `academic_calendar`;
CREATE TABLE `academic_calendar`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `term` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `event_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `event_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `start_date` date NOT NULL,
  `end_date` date NULL DEFAULT NULL,
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_term`(`term` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of academic_calendar
-- ----------------------------
INSERT INTO `academic_calendar` VALUES (1, '2025-2026-2', '春季学期开始', 'ENROLLMENT', '2026-02-24', '2026-02-24', '春季学期正式开始上课', '2026-05-03 12:05:37');
INSERT INTO `academic_calendar` VALUES (2, '2025-2026-2', '清明节假期', 'HOLIDAY', '2026-04-04', '2026-04-06', '清明节法定假日，共3天', '2026-05-03 12:05:37');
INSERT INTO `academic_calendar` VALUES (3, '2025-2026-2', '劳动节假期', 'HOLIDAY', '2026-05-01', '2026-05-05', '劳动节假期，共5天', '2026-05-03 12:05:37');
INSERT INTO `academic_calendar` VALUES (4, '2025-2026-2', '期末考试周', 'EXAM', '2026-06-22', '2026-07-03', '2025-2026学年第二学期期末考试', '2026-05-03 12:05:37');
INSERT INTO `academic_calendar` VALUES (5, '2025-2026-2', '春季学期结束', 'OTHER', '2026-07-10', '2026-07-10', '春季学期结束', '2026-05-03 12:05:37');
INSERT INTO `academic_calendar` VALUES (6, '2025-2026-2', '补考安排', 'EXAM', '2026-08-25', '2026-08-30', '不及格课程补考，需提前申请', '2026-05-03 12:05:37');

-- ----------------------------
-- Table structure for agent_logs
-- ----------------------------
DROP TABLE IF EXISTS `agent_logs`;
CREATE TABLE `agent_logs`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `user_id` bigint NULL DEFAULT NULL,
  `intent` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `user_query` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `rewritten_query` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `recall_count` int NULL DEFAULT 0,
  `rerank_count` int NULL DEFAULT 0,
  `parent_count` int NULL DEFAULT 0,
  `stage1_ms` int NULL DEFAULT 0,
  `stage2_ms` int NULL DEFAULT 0,
  `stage3_ms` int NULL DEFAULT 0,
  `stage4_ms` int NULL DEFAULT 0,
  `stage5_ms` int NULL DEFAULT 0,
  `stage6_ms` int NULL DEFAULT 0,
  `total_ms` int NULL DEFAULT 0,
  `prompt_tokens` int NULL DEFAULT 0,
  `completion_tokens` int NULL DEFAULT 0,
  `hit_docs` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_session_id`(`session_id` ASC) USING BTREE,
  INDEX `idx_created_at`(`created_at` ASC) USING BTREE,
  INDEX `idx_intent`(`intent` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 10 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of agent_logs
-- ----------------------------
INSERT INTO `agent_logs` VALUES (1, '1f934e3758984d5bad48f94398bff7cb', 4, 'ACADEMIC_TOOL', '毕业论文答辩时间安排', NULL, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4485, 0, 0, NULL, '2026-05-03 12:19:36');
INSERT INTO `agent_logs` VALUES (2, '1f934e3758984d5bad48f94398bff7cb', 4, 'ACADEMIC_TOOL', '毕业论文答辩时间安排', NULL, 0, 0, 0, 702, 0, 0, 0, 0, 0, 6424, 0, 0, NULL, '2026-05-03 12:19:50');
INSERT INTO `agent_logs` VALUES (3, '60770d70dd374c6baf66236790722fbe', 5, 'ACADEMIC_TOOL', '查询本学期校历安排', NULL, 0, 0, 0, 0, 0, 0, 0, 0, 0, 6996, 0, 0, 'TOOL:query_academic_calendar', '2026-05-03 12:20:37');
INSERT INTO `agent_logs` VALUES (4, '60770d70dd374c6baf66236790722fbe', 5, 'ACADEMIC_TOOL', '图书馆借阅规则', NULL, 0, 0, 0, 507, 0, 0, 0, 0, 0, 5007, 0, 0, NULL, '2026-05-03 12:22:53');
INSERT INTO `agent_logs` VALUES (5, '7d2b53b826e74747ad7d3618b2c6dc6c', 5, 'POLICY_QA', '查询学校相关政策规定', '学校关于教学管理、学生事务、教职工发展等方面的正式政策与规章制度有哪些？', 20, 20, 6, 0, 3188, 1154, 256, 5, 2479, 7635, 0, 0, '啊倒萨打算|奖学金|阿松大| asda', '2026-05-03 23:53:54');
INSERT INTO `agent_logs` VALUES (6, '0c71a1ffa0d7475c8be80f81a999c849', 4, 'POLICY_QA', '查询学校相关政策规定', '学校关于教学管理、学生事务、教职工发展等方面的正式政策与规章制度有哪些？', 20, 20, 6, 0, 3802, 1117, 414, 0, 2145, 7945, 0, 0, '啊倒萨打算|奖学金|阿松大| asda', '2026-05-04 00:50:23');
INSERT INTO `agent_logs` VALUES (7, '0c71a1ffa0d7475c8be80f81a999c849', 4, 'POLICY_QA', '奖学金评定标准', '学校奖学金评定的具体标准和条件是什么？', 20, 20, 6, 802, 3471, 997, 422, 3, 35609, 41594, 0, 0, '阿松大| asda|啊倒萨打算', '2026-05-04 00:51:12');
INSERT INTO `agent_logs` VALUES (8, '5cd75a7cc95d45a7a5e96d99f9662f08', 5, 'ACADEMIC_TOOL', '本学期什么时候选课', NULL, 0, 0, 0, 0, 0, 0, 0, 0, 0, 10758, 0, 0, 'TOOL:query_course_selection', '2026-05-04 11:39:31');
INSERT INTO `agent_logs` VALUES (9, '60770d70dd374c6baf66236790722fbe', 5, 'POLICY_QA', '如何申请成绩复核？', '成绩复核的申请流程和具体操作步骤是什么？', 20, 20, 6, 0, 2562, 1445, 561, 6, 2085, 7141, 0, 0, '啊倒萨打算', '2026-05-04 12:13:13');

-- ----------------------------
-- Table structure for chat_messages
-- ----------------------------
DROP TABLE IF EXISTS `chat_messages`;
CREATE TABLE `chat_messages`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `user_id` bigint NULL DEFAULT NULL,
  `role` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `intent` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `source_refs` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `tool_calls` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `token_count` int NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_session_id`(`session_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 23 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of chat_messages
-- ----------------------------
INSERT INTO `chat_messages` VALUES (1, '1f934e3758984d5bad48f94398bff7cb', 4, 'user', '毕业论文答辩时间安排', 'ACADEMIC_TOOL', NULL, NULL, 0, '2026-05-03 12:19:36');
INSERT INTO `chat_messages` VALUES (2, '1f934e3758984d5bad48f94398bff7cb', 4, 'assistant', '毕业论文答辩时间通常由各学院自行组织，不属于校历或选课系统的统一安排。为帮您准确获取信息，我需要查询您所在学院的联系方式，以便您进一步咨询具体安排；或者，若您希望直接提交人工咨询工单（例如需教务老师确认本专业答辩日程），我也可以为您创建。\n\n请问：\n- 您是哪个学院/专业的？（例如：计算机学院、经管学院等）  \n- 或者您希望我先帮您查该学院的联系方式？  \n- 还是您更倾向直接创建一个工单，请教务老师提供本学期毕业论文答辩的统一安排？\n\n欢迎告诉我您的偏好 😊', 'ACADEMIC_TOOL', NULL, NULL, 0, '2026-05-03 12:19:36');
INSERT INTO `chat_messages` VALUES (3, '1f934e3758984d5bad48f94398bff7cb', 4, 'user', '毕业论文答辩时间安排', 'ACADEMIC_TOOL', NULL, NULL, 0, '2026-05-03 12:19:50');
INSERT INTO `chat_messages` VALUES (4, '1f934e3758984d5bad48f94398bff7cb', 4, 'assistant', '毕业论文答辩时间通常由各学院根据专业特点和教学进度自行组织，**不包含在校历或选课系统中**，因此需要联系您所在学院的教学办公室或毕业论文工作小组获取具体日程（如答辩分组、时间、地点、材料提交截止日等）。\n\n为帮您高效获取信息，我建议：\n\n✅ **第一步：确认您的学院**（例如：计算机学院、机械工程学院、文学院等）  \n✅ **第二步：我可立即为您查询该学院的官方联系方式（电话/邮箱/办公地址）**，方便您直接咨询；  \n⚠️ 若您不确定学院，或希望由教务处统筹协调（如跨学院问题、流程异常、系统无法查到安排等），我也可以为您创建一个**人工客服工单**，由教务老师主动联系您提供权威答复。\n\n请告诉我：  \n🔹 您所属的学院名称？  \n🔹 或直接说“帮我查计算机学院联系方式” / “请创建工单” — 我马上处理！ 😊', 'ACADEMIC_TOOL', NULL, NULL, 0, '2026-05-03 12:19:50');
INSERT INTO `chat_messages` VALUES (5, '60770d70dd374c6baf66236790722fbe', 5, 'user', '查询本学期校历安排', 'ACADEMIC_TOOL', NULL, NULL, 0, '2026-05-03 12:20:37');
INSERT INTO `chat_messages` VALUES (6, '60770d70dd374c6baf66236790722fbe', 5, 'assistant', '你好！这是2025–2026学年第二学期（即本学期）的校历安排概览 📅：\n\n✅ **春季学期开始**：2026年2月24日（正式上课）  \n🌸 **清明节假期**：2026年4月4日（星期六）至4月6日（星期一），共3天  \n🎉 **劳动节假期**：2026年5月1日（星期五）至5月5日（星期二），共5天  \n📚 **期末考试周**：2026年6月22日（星期一）至7月3日（星期五）  \n🔚 **春季学期结束**：2026年7月10日（星期六）  \n🔧 **补考安排**：2026年8月25日（星期一）至8月30日（星期六），需提前向所在学院申请  \n\n如需查看详细日历表（含教学周划分、实践周、停课调课说明等），我也可以为你进一步整理～欢迎随时告诉我 😊', 'ACADEMIC_TOOL', NULL, NULL, 0, '2026-05-03 12:20:37');
INSERT INTO `chat_messages` VALUES (7, '60770d70dd374c6baf66236790722fbe', 5, 'user', '图书馆借阅规则', 'ACADEMIC_TOOL', NULL, NULL, 0, '2026-05-03 12:22:53');
INSERT INTO `chat_messages` VALUES (8, '60770d70dd374c6baf66236790722fbe', 5, 'assistant', '你好！目前我所接入的教务系统工具中，**暂不支持直接查询图书馆借阅规则**（该信息通常由图书馆独立发布，不属于教务处或院系行政管理范畴）。\n\n不过，我可以为你提供以下帮助：\n\n✅ **推荐操作**：  \n🔹 访问学校图书馆官网（如：https://lib.xxxu.edu.cn），在“读者服务”或“借阅指南”栏目中查看最新规定；  \n🔹 或关注“XX大学图书馆”微信公众号，常有借阅权限、册数限制、借期、续借/预约规则等图文说明；  \n🔹 图书馆服务台电话一般可在官网查到（如需，我可帮你查询图书馆所属管理部门的联系方式 👇）。\n\n需要我帮你查 **图书馆所属部门（如“图书馆办公室”）的联系电话或地址** 吗？😊', 'ACADEMIC_TOOL', NULL, NULL, 0, '2026-05-03 12:22:53');
INSERT INTO `chat_messages` VALUES (9, '7d2b53b826e74747ad7d3618b2c6dc6c', 5, 'user', '查询学校相关政策规定', 'POLICY_QA', NULL, NULL, 0, '2026-05-03 23:53:54');
INSERT INTO `chat_messages` VALUES (10, '7d2b53b826e74747ad7d3618b2c6dc6c', 5, 'assistant', '抱歉，知识库中暂无关于此问题的相关信息，建议您联系相关部门咨询。', 'POLICY_QA', '[{\"docTitle\":\"啊倒萨打算\",\"headingPath\":\"### 中文摘要\",\"pageStart\":1,\"parentId\":\"89facc451de644f0ab3615660e71eafc\"},{\"docTitle\":\"啊倒萨打算\",\"headingPath\":\"### 5.3 行文规范\",\"pageStart\":1,\"parentId\":\"a40b2d61063a468d93eb1e41c6b721fd\"},{\"docTitle\":\"啊倒萨打算\",\"headingPath\":\"4. 用例描述要**具体到操作步骤**，不能只写\\\"管理用户\\\"\",\"pageStart\":1,\"parentId\":\"dd95dd79985a46e187e06bf07ebfa80c\"},{\"docTitle\":\"奖学金\",\"headingPath\":\"二、核心概念（读完你就懂了）\",\"pageStart\":1,\"parentId\":\"dc653c4618ea4158bf2ccedc4bfdbdc3\"},{\"docTitle\":\"阿松大\",\"headingPath\":\"引言\",\"pageStart\":1,\"parentId\":\"7ee88b2a1edf492880d220b1cbf4a27b\"},{\"docTitle\":\" asda\",\"headingPath\":\"引言\",\"pageStart\":1,\"parentId\":\"79f1e88046f74294b883bb51332c3b01\"}]', NULL, 0, '2026-05-03 23:53:54');
INSERT INTO `chat_messages` VALUES (11, '0c71a1ffa0d7475c8be80f81a999c849', 4, 'user', '查询学校相关政策规定', 'POLICY_QA', NULL, NULL, 0, '2026-05-04 00:50:23');
INSERT INTO `chat_messages` VALUES (12, '0c71a1ffa0d7475c8be80f81a999c849', 4, 'assistant', '抱歉，知识库中暂无关于此问题的相关信息，建议您联系相关部门咨询。', 'POLICY_QA', '[{\"docTitle\":\"啊倒萨打算\",\"headingPath\":\"### 中文摘要\",\"pageStart\":1,\"parentId\":\"89facc451de644f0ab3615660e71eafc\"},{\"docTitle\":\"啊倒萨打算\",\"headingPath\":\"### 5.3 行文规范\",\"pageStart\":1,\"parentId\":\"a40b2d61063a468d93eb1e41c6b721fd\"},{\"docTitle\":\"啊倒萨打算\",\"headingPath\":\"4. 用例描述要**具体到操作步骤**，不能只写\\\"管理用户\\\"\",\"pageStart\":1,\"parentId\":\"dd95dd79985a46e187e06bf07ebfa80c\"},{\"docTitle\":\"奖学金\",\"headingPath\":\"二、核心概念（读完你就懂了）\",\"pageStart\":1,\"parentId\":\"dc653c4618ea4158bf2ccedc4bfdbdc3\"},{\"docTitle\":\"阿松大\",\"headingPath\":\"引言\",\"pageStart\":1,\"parentId\":\"7ee88b2a1edf492880d220b1cbf4a27b\"},{\"docTitle\":\" asda\",\"headingPath\":\"引言\",\"pageStart\":1,\"parentId\":\"79f1e88046f74294b883bb51332c3b01\"}]', NULL, 0, '2026-05-04 00:50:23');
INSERT INTO `chat_messages` VALUES (13, '0c71a1ffa0d7475c8be80f81a999c849', 4, 'user', '奖学金评定标准', 'POLICY_QA', NULL, NULL, 0, '2026-05-04 00:51:12');
INSERT INTO `chat_messages` VALUES (14, '0c71a1ffa0d7475c8be80f81a999c849', 4, 'assistant', '根据您提供的参考资料，苏志阳同学获得了多项奖学金，包括国家励志奖学金、本科生一等奖学金等。但是，关于奖学金评定的具体标准和条件，在给定的资料中并没有详细说明。\n\n从苏志阳同学获得的奖项来看，可以推测奖学金的评选可能与以下几个方面有关：\n- 学术成绩优异（如GPA 3.7/4，位于专业前5%）\n- 积极参与科研或项目活动\n- 获得其他荣誉或表彰\n\n不过，具体的评定标准和条件需要参考学校官方发布的相关政策文件。建议直接咨询学校的教务处或者学生事务办公室获取最准确的信息。[来源: 参考资料1及参考资料2, 第1页]\n\n如果需要了解更详细的奖学金申请流程或具体要求，请联系相关部门获取最新指南。', 'POLICY_QA', '[{\"docTitle\":\"阿松大\",\"headingPath\":\"引言\",\"pageStart\":1,\"parentId\":\"7ee88b2a1edf492880d220b1cbf4a27b\"},{\"docTitle\":\" asda\",\"headingPath\":\"引言\",\"pageStart\":1,\"parentId\":\"79f1e88046f74294b883bb51332c3b01\"},{\"docTitle\":\"啊倒萨打算\",\"headingPath\":\"### 5.3 行文规范\",\"pageStart\":1,\"parentId\":\"a40b2d61063a468d93eb1e41c6b721fd\"},{\"docTitle\":\"啊倒萨打算\",\"headingPath\":\"## 一、优秀论文 vs 普通论文：关键差异\",\"pageStart\":1,\"parentId\":\"522152ba14224157962f0e584121fa82\"},{\"docTitle\":\"啊倒萨打算\",\"headingPath\":\"4. 用例描述要**具体到操作步骤**，不能只写\\\"管理用户\\\"\",\"pageStart\":1,\"parentId\":\"dd95dd79985a46e187e06bf07ebfa80c\"},{\"docTitle\":\"啊倒萨打算\",\"headingPath\":\"#### 4.3 数据库设计\",\"pageStart\":1,\"parentId\":\"f41554477fc14f1497e0f39bdf0e133d\"}]', NULL, 0, '2026-05-04 00:51:12');
INSERT INTO `chat_messages` VALUES (15, '5cd75a7cc95d45a7a5e96d99f9662f08', 5, 'user', '本学期什么时候选课', 'ACADEMIC_TOOL', NULL, NULL, 0, '2026-05-04 11:39:31');
INSERT INTO `chat_messages` VALUES (16, '5cd75a7cc95d45a7a5e96d99f9662f08', 5, 'assistant', '你好！2025–2026学年第二学期（即本学期）的选课安排如下，供你参考👇：\n\n✅ **第一轮选课（大三大四）**  \n- 时间：2026年1月15日（周四）08:00 — 1月17日（周六）22:00  \n- 对象：大三、大四学生  \n- 说明：优先选本专业必修课  \n\n✅ **第一轮选课（大一大二）**  \n- 时间：2026年1月18日（周日）08:00 — 1月20日（周二）22:00  \n- 对象：大一、大二学生  \n\n✅ **第二轮补选（全体开放）**  \n- 时间：2026年2月25日（周三）08:00 — 2月28日（周六）22:00  \n- 说明：开学后补选，课程名额以系统实时剩余为准  \n\n✅ **退课阶段（全体可退）**  \n- 时间：2026年3月4日（周三）08:00 — 3月7日（周六）22:00  \n- 说明：可退已选课程，退课后名额将释放供他人补选  \n\n📌 温馨提示：建议提前查看培养方案和课表，关注教务处官网或「SmartCampus」App 的选课提醒，避免错过关键节点哦！\n\n如需查询某门课是否开放、选课冲突处理，或需要人工协助（如选课异常、权限问题），我也可以帮你转接教务老师～ 😊', 'ACADEMIC_TOOL', NULL, NULL, 0, '2026-05-04 11:39:31');
INSERT INTO `chat_messages` VALUES (17, '5cd75a7cc95d45a7a5e96d99f9662f08', 5, 'user', '申请转人工', 'HUMAN_HANDOFF', NULL, NULL, 0, '2026-05-04 11:55:17');
INSERT INTO `chat_messages` VALUES (18, '5cd75a7cc95d45a7a5e96d99f9662f08', 5, 'assistant', '已为你转接人工老师。你可以继续在这里补充问题，老师会看到当前会话记录并回复。', 'HUMAN_HANDOFF', NULL, NULL, 0, '2026-05-04 11:55:17');
INSERT INTO `chat_messages` VALUES (19, '5cd75a7cc95d45a7a5e96d99f9662f08', 5, 'teacher', '好的', 'HUMAN_HANDOFF', NULL, NULL, 0, '2026-05-04 11:55:41');
INSERT INTO `chat_messages` VALUES (20, '60770d70dd374c6baf66236790722fbe', 5, 'user', '如何申请成绩复核？', 'POLICY_QA', NULL, NULL, 0, '2026-05-04 12:13:13');
INSERT INTO `chat_messages` VALUES (21, '60770d70dd374c6baf66236790722fbe', 5, 'assistant', '抱歉，知识库中暂无关于此问题的相关信息，建议您联系相关部门咨询。', 'POLICY_QA', '[{\"docTitle\":\"啊倒萨打算\",\"headingPath\":\"4. 用例描述要**具体到操作步骤**，不能只写\\\"管理用户\\\"\",\"pageStart\":1,\"parentId\":\"dd95dd79985a46e187e06bf07ebfa80c\"},{\"docTitle\":\"啊倒萨打算\",\"headingPath\":\"#### 1.1 研究背景与意义\",\"pageStart\":1,\"parentId\":\"2350b95812894a3d933bb6802112c16a\"},{\"docTitle\":\"啊倒萨打算\",\"headingPath\":\"## 一、优秀论文 vs 普通论文：关键差异\",\"pageStart\":1,\"parentId\":\"522152ba14224157962f0e584121fa82\"},{\"docTitle\":\"啊倒萨打算\",\"headingPath\":\"#### 6.2 功能测试\",\"pageStart\":1,\"parentId\":\"350e1b89a24a400c84c2bf407b6c98cd\"},{\"docTitle\":\"啊倒萨打算\",\"headingPath\":\"引言\",\"pageStart\":1,\"parentId\":\"8f787b47b34e4156a28f404d2b43e3bd\"},{\"docTitle\":\"啊倒萨打算\",\"headingPath\":\"#### 1.3 研究内容与论文结构\",\"pageStart\":1,\"parentId\":\"53504b820e7f49b4b840ad700aaa7d75\"}]', NULL, 0, '2026-05-04 12:13:13');
INSERT INTO `chat_messages` VALUES (22, '5cd75a7cc95d45a7a5e96d99f9662f08', 5, 'teacher', '同学你好，老师已收到你的问题，我会先核对相关信息后尽快回复你。', 'HUMAN_HANDOFF', NULL, NULL, 0, '2026-05-04 13:02:16');

-- ----------------------------
-- Table structure for chat_sessions
-- ----------------------------
DROP TABLE IF EXISTS `chat_sessions`;
CREATE TABLE `chat_sessions`  (
  `session_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `user_id` bigint NOT NULL,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ACTIVE',
  `message_count` int NOT NULL DEFAULT 0,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`session_id`) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of chat_sessions
-- ----------------------------
INSERT INTO `chat_sessions` VALUES ('0c71a1ffa0d7475c8be80f81a999c849', 4, '查询学校相关政策规定', 'ACTIVE', 4, '2026-05-04 00:50:23', '2026-05-04 00:51:11');
INSERT INTO `chat_sessions` VALUES ('1f934e3758984d5bad48f94398bff7cb', 4, '毕业论文答辩时间安排', 'ACTIVE', 4, '2026-05-03 12:19:36', '2026-05-03 12:19:50');
INSERT INTO `chat_sessions` VALUES ('5cd75a7cc95d45a7a5e96d99f9662f08', 5, '本学期什么时候选课', 'ACTIVE', 6, '2026-05-04 11:39:31', '2026-05-04 13:02:15');
INSERT INTO `chat_sessions` VALUES ('60770d70dd374c6baf66236790722fbe', 5, '查询本学期校历安排', 'ACTIVE', 6, '2026-05-03 12:20:37', '2026-05-04 12:13:13');
INSERT INTO `chat_sessions` VALUES ('7d2b53b826e74747ad7d3618b2c6dc6c', 5, '查询学校相关政策规定', 'ACTIVE', 2, '2026-05-03 23:53:54', '2026-05-03 23:53:54');
INSERT INTO `chat_sessions` VALUES ('demo-s001', 1, '转专业绩点不够如何处理', 'ACTIVE', 4, '2026-05-03 12:05:37', '2026-05-03 12:05:37');
INSERT INTO `chat_sessions` VALUES ('demo-s002', 1, '宿舍热水器故障三天无人修理', 'HUMAN_PENDING', 2, '2026-05-03 12:05:37', '2026-05-03 12:05:37');
INSERT INTO `chat_sessions` VALUES ('demo-s003', 1, '助学金评定结果有异议', 'HUMAN_PENDING', 3, '2026-05-03 12:05:37', '2026-05-03 12:05:37');

-- ----------------------------
-- Table structure for child_chunks
-- ----------------------------
DROP TABLE IF EXISTS `child_chunks`;
CREATE TABLE `child_chunks`  (
  `child_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `parent_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `doc_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `doc_title` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `heading_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `chunk_index` int NOT NULL DEFAULT 0,
  `start_offset` int NULL DEFAULT NULL,
  `end_offset` int NULL DEFAULT NULL,
  `token_count` int NOT NULL DEFAULT 0,
  `page_start` int NULL DEFAULT NULL,
  `page_end` int NULL DEFAULT NULL,
  `category` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `access_level` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`child_id`) USING BTREE,
  INDEX `idx_parent_id`(`parent_id` ASC) USING BTREE,
  INDEX `idx_doc_id`(`doc_id` ASC) USING BTREE,
  INDEX `idx_category`(`category` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'Child Chunk（检索用）' ROW_FORMAT = Dynamic;


-- ----------------------------
-- Table structure for chunk_inverted_index
-- ----------------------------
DROP TABLE IF EXISTS `chunk_inverted_index`;
CREATE TABLE `chunk_inverted_index`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `child_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `doc_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `term` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `tf` int NOT NULL DEFAULT 1 COMMENT '词频',
  `doc_len` int NOT NULL DEFAULT 0 COMMENT '文档字符数',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_term`(`term` ASC) USING BTREE,
  INDEX `idx_child_id`(`child_id` ASC) USING BTREE,
  INDEX `idx_doc_id`(`doc_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7427 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'BM25倒排索引' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of chunk_inverted_index
-- ----------------------------

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `password` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'SHA-256',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'STUDENT' COMMENT 'STUDENT/TEACHER/ADMIN',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `avatar_url` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `college` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username`(`username` ASC) USING BTREE,
  UNIQUE INDEX `email`(`email` ASC) USING BTREE,
  INDEX `idx_role`(`role` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of users
-- ----------------------------
INSERT INTO `users` VALUES (1, 'admin001', 'admin@campus.edu', '4c5fe88e8c9fd1f1de2cd52aadf0c4fb7e4b7adb0ce8f78a0b31040e4e3cb76a', 'ADMIN', '系统管理员', NULL, '教务处', 1, '2026-05-03 12:05:37', '2026-05-03 12:05:37');
INSERT INTO `users` VALUES (2, 'teacher001', 'teacher@campus.edu', '4c5fe88e8c9fd1f1de2cd52aadf0c4fb7e4b7adb0ce8f78a0b31040e4e3cb76a', 'TEACHER', '张老师', NULL, '计算机学院', 1, '2026-05-03 12:05:37', '2026-05-03 12:05:37');
INSERT INTO `users` VALUES (3, 'student001', 'student@campus.edu', '4c5fe88e8c9fd1f1de2cd52aadf0c4fb7e4b7adb0ce8f78a0b31040e4e3cb76a', 'STUDENT', '李同学', NULL, '计算机学院', 1, '2026-05-03 12:05:37', '2026-05-03 12:05:37');
INSERT INTO `users` VALUES (4, 'simon', 'simon@qq.com', '94edf28c6d6da38fd35d7ad53e485307f89fbeaf120485c8d17a43f323deee71', 'STUDENT', 'simon', NULL, NULL, 1, '2026-05-03 12:19:27', '2026-05-03 12:19:27');
INSERT INTO `users` VALUES (5, 'admin', 'admin@qq.com', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', 'TEACHER', 'admin', NULL, NULL, 1, '2026-05-03 12:20:20', '2026-05-03 12:20:20');
INSERT INTO `users` VALUES (6, 'debugteacher', 'debugteacher@example.com', '1919f16502fc8d9fcdefdf9c4195e27854feb8c529c1797b128ecb5e2737ff7f', 'TEACHER', 'Debug Teacher', NULL, NULL, 1, '2026-05-03 22:24:40', '2026-05-03 22:24:40');

SET FOREIGN_KEY_CHECKS = 1;
