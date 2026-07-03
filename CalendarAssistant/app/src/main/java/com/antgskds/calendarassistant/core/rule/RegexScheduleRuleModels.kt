package com.antgskds.calendarassistant.core.rule

import com.antgskds.calendarassistant.calendar.models.EventTags
import kotlinx.serialization.Serializable

@Serializable
data class RegexScheduleRule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val pattern: String,
    val titleTemplate: String = "{title}",
    val tag: String = EventTags.GENERAL,
    val dateGroup: String = "date",
    val timeGroup: String = "time",
    val titleGroup: String = "title",
    val locationGroup: String = "location",
    val locationTemplate: String = "",
    val descriptionTemplate: String = "",
    val useCurrentTimeWhenMissing: Boolean = false,
)

object RegexScheduleRuleDefaults {
    private const val DATE_PATTERN = "今天|明天|后天|\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?|\\d{1,2}月\\d{1,2}日?|周[一二三四五六日天]|星期[一二三四五六日天]"
    private const val TIME_PATTERN = "(?:上午|下午|晚上|中午|凌晨|早上)?\\s*\\d{1,2}(?::\\d{1,2}|点(?:半|\\d{1,2}分?)?)?"
    private const val CLEAR_TIME_PATTERN = "(?:上午|下午|晚上|中午|凌晨|早上)?\\s*\\d{1,2}(?::\\d{1,2}|点(?:半|\\d{1,2}分?)?)"

    fun rules(): List<RegexScheduleRule> {
        return listOf(
            RegexScheduleRule(
                id = "date_time_title",
                name = "日期时间标题",
                pattern = "(?<date>$DATE_PATTERN)\\s*(?<time>$TIME_PATTERN)\\s*(?<title>[^，,。；;\\n]+)"
            ),
            RegexScheduleRule(
                id = "title_date_time",
                name = "标题日期时间",
                pattern = "(?<title>[^，,。；;\\n]{2,24}?)\\s*(?<date>$DATE_PATTERN)\\s*(?<time>$TIME_PATTERN)"
            ),
            RegexScheduleRule(
                id = "train_order",
                name = "列车订单",
                pattern = "(?s)(?=.*(?:火车票|12306|高铁|动车|列车|车次|检票口|候车|发车|开车))(?=.*?(?<train>[GDCZTKY]\\d{1,5})\\s*(?<time>$CLEAR_TIME_PATTERN)\\s*(?<date>$DATE_PATTERN)(?:\\s*周[一二三四五六日天])?\\s*(?:$CLEAR_TIME_PATTERN)?\\s*(?<from>[\\p{IsHan}A-Za-z]{2,16})\\s*(?<to>[\\p{IsHan}A-Za-z]{2,16}))(?:(?=.*?(?:检票口|检票)\\s*(?<gate>[A-Za-z0-9一二三四五六七八九十东西南北楼候车室]{1,12})))?(?:(?=.*?(?<seat>\\d{1,2}车[A-Za-z0-9]{1,4}[^\\s\\n，,。；;]{0,8}|硬座|软座|硬卧|软卧|商务座|一等座|二等座)))?.*",
                titleTemplate = "🚄 {train} {from}-{to}",
                tag = EventTags.TRAIN,
                locationGroup = "from",
                locationTemplate = "{from} -> {to}",
                descriptionTemplate = "{train}|{gate}|{seat}",
            ),
            RegexScheduleRule(
                id = "flight_order",
                name = "航班订单",
                pattern = "(?s)(?=.*(?:电子登机牌|航班|机票|登机口|登机时间|登机门|机场|值机|起飞))(?=.*?(?<date>$DATE_PATTERN))(?=.*?(?<from>[\\p{IsHan}A-Za-z]{2,16})\\s*(?<to>[\\p{IsHan}A-Za-z]{2,16})\\s*(?<fromCode>[A-Z]{3})\\s*(?<flight>[A-Z]{2}\\d{3,4}|[A-Z0-9]{2}\\d{3,4})\\s*(?<toCode>[A-Z]{3}))(?=.*?(?:登机时间|起飞|出发)?\\s*(?<time>$CLEAR_TIME_PATTERN))(?:(?=.*?(?:登机口|登机门|口)\\s*(?<gate>[A-Za-z0-9]{1,8})))?(?:(?=.*?(?:座位号|座位|座号|座)\\s*(?<seat>[A-Za-z0-9\\-]{1,8})))?.*",
                titleTemplate = "✈️ {flight} {from}-{to}",
                tag = EventTags.FLIGHT,
                locationGroup = "from",
                locationTemplate = "{from}({fromCode}) -> {to}({toCode})",
                descriptionTemplate = "{flight}|{gate}|{seat}",
            ),
            RegexScheduleRule(
                id = "taxi_order",
                name = "打车行程",
                pattern = "(?s)(?=.*(?:滴滴|高德|T3出行|曹操|哈啰|网约车|出租车|快车|专车|顺风车|用车|司机|车牌|行程中|预约单|已接单|出发|上车))(?:(?=.*?(?:(?<date>$DATE_PATTERN)\\s*)?(?<time>$CLEAR_TIME_PATTERN)\\s*(?:出发|上车)?[^\\n]*\\n(?<from>[^\\n]{2,32})\\s*(?<to>[^\\n]{2,32})))?(?=.*?(?<plate>[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼][A-Z][A-Z0-9]{5,6}))(?:(?=.*?(?<car>(?<color>黑|白|银|灰|红|蓝|绿|黄|橙|金|棕|黑色|白色|银色|灰色|红色|蓝色|绿色|黄色|橙色|金色|棕色)[·・\\s]*(?<model>(?:特斯拉|比亚迪|大众|丰田|本田|奔驰|宝马|奥迪|日产|小鹏|理想|蔚来|吉利|长安|埃安|荣威|现代|起亚|广汽埃安)[\\p{IsHan}A-Za-z0-9 +.\\-]{0,16}|[\\p{IsHan}A-Za-z0-9 +.\\-]{1,20}(?:SUV|MPV|轿车|汽车)))))?(?:(?=.*?(?<platform>滴滴|高德|T3|曹操|哈啰|花小猪|首汽|享道|如祺|出租车)))?.*",
                titleTemplate = "🚖 {car} {plate}",
                tag = EventTags.TAXI,
                dateGroup = "date",
                timeGroup = "time",
                locationGroup = "",
                locationTemplate = "{from} -> {to}",
                descriptionTemplate = "{color}|{model}|{plate}",
                useCurrentTimeWhenMissing = true,
            ),
            RegexScheduleRule(
                id = "pickup_detail",
                name = "取件通知多行",
                pattern = "(?s)(?=.*(?:取件|快递|代收|驿站|待领取))(?=.*?(?:取件码|取货码|提货码|提货号|取件编号|签收码|签收编号|收货码|提货编码)\\s*(?:为|是|:|：|=|#|＃)?\\s*\\*?(?<code>[A-Za-z0-9][A-Za-z0-9\\s-]{1,18}[A-Za-z0-9]))(?:(?=.*?(?<brand>菜鸟驿站|[\\p{IsHan}A-Za-z]{2,12}(?:快递|速递|物流|驿站|代收点))))?(?:(?=.*?(?:取件地址|收货地址|地址|位置)\\s*(?<location>[^\\n]{2,48})))?(?:(?=.*?(?<time>$CLEAR_TIME_PATTERN)))?.*",
                titleTemplate = "📦 {brand} {code}",
                tag = EventTags.PICKUP,
                dateGroup = "",
                timeGroup = "time",
                locationGroup = "location",
                locationTemplate = "{location}",
                descriptionTemplate = "{code}|{brand}|{location}",
                useCurrentTimeWhenMissing = true,
            ),
            RegexScheduleRule(
                id = "food_detail",
                name = "取餐通知多行",
                pattern = "(?s)(?=.*(?:取餐|取餐号|取餐码|餐码|餐号|自提号|自提码|到店自取|外卖))(?=.*?(?:取餐码|取餐号|餐码|餐号|自提号|自提码|自取码|到店取码|取餐编号|外卖码)\\s*(?:为|是|:|：|=|#|＃)?\\s*\\*?(?<code>[A-Za-z0-9][A-Za-z0-9\\s-]{0,18}[A-Za-z0-9]))(?:(?=.*?(?:预计)?(?<time>$CLEAR_TIME_PATTERN)\\s*可取\\s*(?<location>[^\\n]{2,48})))?(?:(?=.*?(?<brand>蜜雪冰城|奈雪的茶|瑞幸咖啡|星巴克|麦当劳|肯德基|喜茶|库迪咖啡|[\\p{IsHan}A-Za-z]{2,16}(?:店|茶|咖啡|食堂))))?.*",
                titleTemplate = "🍔 {brand} {code}",
                tag = EventTags.FOOD,
                dateGroup = "",
                timeGroup = "time",
                locationGroup = "location",
                locationTemplate = "{location}",
                descriptionTemplate = "{code}|{brand}|{location}",
                useCurrentTimeWhenMissing = true,
            ),
            RegexScheduleRule(
                id = "ticket_detail",
                name = "取票通知多行",
                pattern = "(?s)(?=.*(?:取票|票码|电子票|入场码|检票码|演出时间|观影码))(?=.*?(?:取票码|取票号|票码|票号|电子票码|兑票码|入场码|入园码|检票码|观影码|凭证码|验票码)\\s*(?:为|是|:|：|=|#|＃)?\\s*\\*?(?<code>[A-Za-z0-9][A-Za-z0-9\\s-]{1,24}[A-Za-z0-9]))(?:(?=.*?(?:演出时间|观影时间|入场时间|开始时间)\\s*(?<date>$DATE_PATTERN)\\s*(?<time>$CLEAR_TIME_PATTERN)))?(?:(?=.*?(?:取票地点|取票地址|地点|地址)\\s*(?<location>[^\\n]{2,48})))?(?:(?=.*?(?<brand>大麦|猫眼|淘票票|保利票务|[\\p{IsHan}A-Za-z]{2,16}(?:票务|剧院|影院|影城))))?.*",
                titleTemplate = "🎫 {brand} 取票 {code}",
                tag = EventTags.TICKET,
                locationGroup = "location",
                locationTemplate = "{location}",
                descriptionTemplate = "{code}|{brand}|{location}",
                useCurrentTimeWhenMissing = true,
            ),
            RegexScheduleRule(
                id = "sender_detail",
                name = "寄件通知多行",
                pattern = "(?s)(?=.*(?:寄件|寄件码|退货码|揽收码|上门取件|到店交寄))(?=.*?(?:寄件码|寄件号|寄件编号|寄货码|退货码|退件码|揽收码|上门取件码)\\s*(?:为|是|:|：|=|#|＃)?\\s*\\*?(?<code>[A-Za-z0-9][A-Za-z0-9\\s-]{1,24}[A-Za-z0-9]))(?:(?=.*?(?<date>今天|明天|后天|\\d{4}[-/.年]\\d{1,2}[-/.月]\\d{1,2}日?|\\d{1,2}月\\d{1,2}日?)\\s*(?<time>$CLEAR_TIME_PATTERN)))?(?:(?=.*?(?:寄件门店|寄件地点|寄件地址|门店|地点|地址)\\s*(?<location>[^\\n]{2,48})))?(?:(?=.*?(?<brand>顺丰速运|顺丰|京东物流|德邦|中通|圆通|申通|韵达|极兔|[\\p{IsHan}A-Za-z]{2,12}(?:快递|速运|物流))))?.*",
                titleTemplate = "🚚 {brand} 寄件 {code}",
                tag = EventTags.SENDER,
                locationGroup = "location",
                locationTemplate = "{location}",
                descriptionTemplate = "{code}|{brand}|{location}",
                useCurrentTimeWhenMissing = true,
            ),
            RegexScheduleRule(
                id = "pickup_code",
                name = "取件码",
                pattern = "(?<label>取件码|取货码|提货码|提货号|取件编号|签收码|签收编号|收货码|提货编码)\\s*(?:为|是|:|：|=|#|＃)?\\s*\\*?(?<code>[A-Za-z0-9][A-Za-z0-9\\s-]{1,18}[A-Za-z0-9])",
                titleTemplate = "📦 取件 {code}",
                tag = EventTags.PICKUP,
                dateGroup = "",
                timeGroup = "",
                locationGroup = "",
                descriptionTemplate = "{code}||",
                useCurrentTimeWhenMissing = true,
            ),
            RegexScheduleRule(
                id = "food_code",
                name = "取餐码",
                pattern = "(?<label>取餐码|取餐号|餐码|餐号|自提码|自取码|到店取码|取餐编号|外卖码)\\s*(?:为|是|:|：|=|#|＃)?\\s*\\*?(?<code>[A-Za-z0-9][A-Za-z0-9\\s-]{1,18}[A-Za-z0-9])",
                titleTemplate = "🍔 取餐 {code}",
                tag = EventTags.FOOD,
                dateGroup = "",
                timeGroup = "",
                locationGroup = "",
                descriptionTemplate = "{code}||",
                useCurrentTimeWhenMissing = true,
            ),
            RegexScheduleRule(
                id = "ticket_code",
                name = "取票码",
                pattern = "(?<label>取票码|取票号|票码|票号|电子票码|兑票码|入场码|入园码|检票码|观影码|凭证码|验票码)\\s*(?:为|是|:|：|=|#|＃)?\\s*\\*?(?<code>[A-Za-z0-9][A-Za-z0-9\\s-]{1,18}[A-Za-z0-9])",
                titleTemplate = "🎫 取票 {code}",
                tag = EventTags.TICKET,
                dateGroup = "",
                timeGroup = "",
                locationGroup = "",
                descriptionTemplate = "{code}||",
                useCurrentTimeWhenMissing = true,
            ),
            RegexScheduleRule(
                id = "sender_code",
                name = "寄件码",
                pattern = "(?<label>寄件码|寄件号|寄件编号|寄货码|退货码|退件码|揽收码|上门取件码)\\s*(?:为|是|:|：|=|#|＃)?\\s*\\*?(?<code>[A-Za-z0-9][A-Za-z0-9\\s-]{1,18}[A-Za-z0-9])",
                titleTemplate = "🚚 寄件 {code}",
                tag = EventTags.SENDER,
                dateGroup = "",
                timeGroup = "",
                locationGroup = "",
                descriptionTemplate = "{code}||",
                useCurrentTimeWhenMissing = true,
            ),
        )
    }
}
