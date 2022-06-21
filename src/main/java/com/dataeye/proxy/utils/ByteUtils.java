package com.dataeye.proxy.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author jaret
 * @date 2022/5/16 20:23
 * @description
 */
public class ByteUtils {

    public static void main(String[] args) throws IOException {
//        System.out.println(parseOctal("\\346\\213\\274\\345\\244\\232\\345\\244\\232"));
////        String json = "";
//        String json = "{\"iPhone13限时9块9(adTitle)\":3,\"鹏菁\":24,\"拼多多\":377,\"钱坤免费诊股 提供的广告\":7,\"鹏菁 提供的广告\":13,\"突发降价：华为手机限时9块9抢！\":31,\"几乎白送！速来！(adTitle)\":2,\"拼多多 提供的广告\":123,\"卡寇丝服饰商行\":2,\"觅知心理\":5,\"iPhone13 Pro Max(adTitle)\":4,\"帅泓魏\":2,\"宋晓峰花式作精 上演捧腹喜剧(adTitle)\":1,\"李宁卫衣，秋冬穿超舒服！1元抢！\":35,\"腾讯扣叮 提供的广告\":32,\"高教授淡斑微晶贴\":10,\"点石乐投\":9,\"中国联通\":5,\"支持国货！李宁双肩包1元1个！太划算了！\":1,\"上汽大众\":19,\"魔狱奇迹-MU怀旧复古奇迹\":102,\"山河梦情 – 魔道江湖情仇国风仙侠游戏!\":225,\"重磅福利！iPhone13首降！限时限量9块9抢(adTitle)\":3,\"携程旅行\":1,\"2年0利率购全新途昂X，置换还可享4000元补贴，立即预约试驾体验\":1,\"阿瓦隆之王\":55,\"高教授淡斑微晶贴 提供的广告\":41,\"皮尔卡丹品牌专营店\":23,\"iPhone13 Pro Max\":6,\"闲置名牌手表2022新品牌特卖会，全场低至1折！\":1,\"中国联通 提供的广告\":9,\"凌云诺\":2,\"大天使领域-新纪元\":4,\"腾讯扣叮\":26,\"浮生为卿歌\":33,\"限时限量！拼多多iPhone13 Pro Max！9块9抢！\":35,\"帅泓魏 提供的广告\":17,\"9.9元抢iPhone 13 Pro春季新款苍岭绿色！别犹豫！\":1,\"穿上轻盈不闷脚！运动鞋1元1双抢！\":31,\"喜剧人来袭，笑点逐渐绽放！\":1,\"贝叶集\":1,\"卡寇丝服饰商行 提供的广告\":25,\"点石乐投 提供的广告\":47,\"传奇1.76怀旧版-巅峰霸业\":3,\"小红书 – 你的生活指南\":50,\"凡是腋下异味、有油耳朵，谨记这个方法，轻松改善，再近也闻不到\":9}";
//        JSONObject jsonObject = JSONObject.parseObject(json);
//        HashSet<String> data = new HashSet<>();
//        jsonObject.forEach((k, v) -> {
//            if (k.contains("adTitle")) {
//                String replace = k.replace("(adTitle)", "");
//                data.add(replace);
//            }
//        });
//        System.out.println(JSON.toJSONString(data));

//        HashSet<String> data = new HashSet<>();
//        String path = "C:\\Users\\caiguanghui\\Desktop\\新文件 1.txt";
//        List<String> lines = FileUtils.readLines(new File(path), StandardCharsets.UTF_8);
//        for (String line : lines) {
//            if (StringUtils.isBlank(line)) {
//                continue;
//            }
//            JSONObject word = JSONObject.parseObject(line);
//            word.forEach((k, v) -> {
//                if (k.contains("adTitle")) {
//                    String replace = k.replace("(adTitle)", "");
//                    data.add(replace);
//                }
//            });
//        }
//        System.out.println(JSON.toJSONString(data));
        String str = "{\"code\":502,\"msg\":\"Bad Gateway\",\"targetUrl\":\"https://mazu.m.qq.com\",\"proxyIp\":\"114.99.220.99\"," +
                "\"proxyPort\":4278,\"" +
                "headers\":{\"User-Agent\":\"live4iphoneRel/8.6.00 (iPhone; iOS 15.4.1; Scale/3.00)\"}," +
                "\"body\":\"\\u0002\\u0000\\u0000\\u0000\\u0006\\\"\\u0000\\u0000\\u0000\\u0000:\\u0002\\u0000\\u0000\\u0000\\u0002\\u0012\\u0000\\u0000\\u0000\\u0000& 03039122051214252600092103705803R\\u0000\\u0000N[b\\u0000\\u0000\\u0000\\u0000�\\u0002\\u0000\\u0000\\u0000\\u0000\\u000B\\u000BI\\u0002\\u0000\\u0000\\u0000\\u0001\\n\\u0002\\u0000\\u0000\\u001F�\\u0012\\u0000\\u0000\\u0000\\u0007=\\u0000\\u0002\\u0000\\u0000\\u0005�Ij8\\n֕�A��&�.\\u001F25Ƥ�h+�~\\u0005%\\u001E�I�6�y��0��#ٷ#\\u001DyT��\\b\\u0011\\u000E\\u0017FٱW\\u0003���$r�RM�\\u0018����a�dc�\\u0018��\\u0001���־g\\u0017$\\bn2'��\\u0014���\\u0014�6\\u0016�HU�D�=��� \\u0003�$,s��WsR�\\fc�\\u000B��(^\\u0007?�p��\\u0001(�m->c�R\\u0015h���%tI\\u0000ZY�e�G��\\u001A�)�\\u007F�\\u001E\\u0011\\u001E�\\u0011;\\u0013�,oܨE� �e����>{�ؠ\\u001E\\u0016)\\t�^�/�\\u007Fa�0?Ѽ�\\u0019�4m\\u0019���u�����^R��\\u0019b}r���Dj<\\u0007.�y��ղ��\\\"P\\rH�~�2��\\u001FZ�M\\u0001�83{̤�2�\\tX�<����p�iY���\u05CF\\u000FH2H�͌��v\\u000B�\\u001F{N��!D�ucGA���\\u001C}��:\\u0001�\\f NUـn����eF\\u001B�H��!5��w��H��(�vG�y\\u0019��\\u0007�y� Ldm�I��w]��^KC5��=���\\u0014`\\u001APW7F?j�\\u0017/KεҾ�i��sH�S!{v�ͼ,��T���`�G����W�4�\\u0005J$�\\u001A2I����\\u0003�O��z �nm\\u001Bo\\u0003\\u001F�����ܛ�-�\\u0004�\\u001B�\\\\�\\u0016�����v5�ʈ$\uE706O���)���wS\\u001B�46��!TO��ǚ\\t���yK}\\u001BsN��u����o��\\u0015\\u007F듶�a�h�3(�~DD��I����\\u0010\\u0001E�\\u0019\\u0014���9�I�\\u0005�Y�/����\\b\\n�\\u001E\\r�������*�7��[G�\\u000Eb����J��iϜ��rS��\\f�A +?\\u001Ea\\u0010�)�/M��\\u00199߮��}��o���6��T2c<P���\\u0013�y\\u001AS~�'�;���\\u007Fa列\\u0006���nŽ[��yX�\\u000E\\u0005\\u001CLz=dgB�����GsgN�/�[9]�C]ٞ\\u0014��\\u0017�d-u1�\\u001C�\\tY�{Ë���rS��7k&���3�:1\\u001A\\u000Eۍ��\\u0016����S��N����$<���q�\\u0006HJ���\\t��@r�A&��VC��ph\\nz�v\\u000E�B<�$˝PU\\ne{���h\\u0010Ŀ�M#\\f���R\\t�Z�m-|,�۹J�蘹2ApN���vG)3[G���t�\\b9�\\u0014������[�;�xC�>��4~\\u0013��M�\\u0014v�$�^\\u0004떝�\\\\\\\"�0k\u070E�{��\\u0002u\\b����6����\\u0005>H���\\r���ab~<\\u001C\\u0013\\r\\u000E�XZ�/֓�;�T�\\u001B�W\\u007F�C����wu�V&�\\u007F�v�kV�\\u00149Q1�M��,$\\u0007��*\\u0006�@�\\u001F�dez�� �\\u000B��O��U'~������_�Oڬ���%��\\u00198�\\u001E\\u0003�f�\\u0001�m�|M\\u000Bic�\\u0001�~\\u001C��\\u0014\\u0014{�i�Ŋ�i\\\"v��/z��j����Cy�\\u0005\\u0001�F���\\u007F��22�r�(\\\"��M\\u000F�\\u001B�c�\\u0012���B�>\\f����bZ�q2" +
                "�_+A�3d3\\r\\u000F ����$��N���\u074B���\\u0006٬L)h�L��J��\\u0016�\\u0012�+X���U��\\n,�^" +
                "�\\u00168\\n\\u0014p\\u0012b�8\\u0003t]l�J�\\u00044����F�I���\\u000F����`��u�\\u0007u\\t�P\\u0014L<hD\\u0006�@" +
                "�:�9�\\n�E��~K?үM��\\u001Fj\\u0007�\\u000B\\u0004�9�~0���>��I�/G�\\u0018��8�3������\\u0001�����" +
                "M#���ۇ\\u001Eo��\\u001DiK�: /��Ę�<���G�\\u001E\\u0002��\\u001E�q\\u0010\\u0013�\\u001F#�k�ZG=P�LK����hV��B��Q\\u0005�" +
                "���W2\\bo�\\u001Bu�s�́]���\\t��M�3f�-��CR\\u0000\\u0000\\u0000\\u0000\\u000B\"}";
        byte[] bytes = str.getBytes();
        System.out.println(bytes);
    }

    /**
     * 解析八进制
     *
     * @param message
     * @return
     */
    public static String parseOctal(String message) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(message.getBytes());
        int read = -1;
        byte[] byte3 = new byte[3];
        while ((read = inputStream.read()) > -1) {
            if (read == '\\') {
                try {
                    inputStream.read(byte3);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                outputStream.write(Integer.parseInt(new String(byte3), 8));
            } else {
                outputStream.write(read);
            }
        }
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }

}
