package com.fulldive.chatting.handler;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class WebSocketHandler extends TextWebSocketHandler{

    private static final ConcurrentHashMap<String, WebSocketSession> CLIENTS = new ConcurrentHashMap<String, WebSocketSession>();
    private Map<WebSocketSession, String> sessionList = new ConcurrentHashMap<WebSocketSession, String>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String serverIp = "http://fulldive.live:8880";
    private final Map<String, Map<String ,Object>> ClientList = new HashMap<>();

    private static int i;

    /**
     * 현재시간 반환
     */
    public String currentTime() {
        // 현재 시간을 가져옵니다.
        LocalDateTime currentTime = LocalDateTime.now();

        // 날짜와 시간을 원하는 형식으로 포맷팅합니다.
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        String formattedTime = currentTime.format(formatter);

        // 결과를 출력합니다.
        System.out.println("현재 시간: " + formattedTime);

        return formattedTime;
    }
    /**
     * 연결 성공 시
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        Map<String, Object> inputUserSession = new HashMap<>();

        String urlQuery = session.getUri().getQuery();
        String uniqueId = session.getId();
        System.out.println("uniqueId:" + uniqueId);
        String[] urlQuerySplit = urlQuery.split(",");

        System.out.println("urlQuery: " + urlQuery );
        System.out.println("urlQuerySplit: " + urlQuerySplit.length );

        for(String x : urlQuerySplit) {
            String[] inputMap = x.split("=");
            for(int i=0; i<x.length(); i++) {
                inputUserSession.put(inputMap[0], inputMap[1]);
            }
        }

        String id = (String) inputUserSession.get("roomId");
        String userId = (String) inputUserSession.get("userId");

        if(ClientList.get(id) != null) {
            List<Map<String, Object>> userList = (List<Map<String, Object>>) ClientList.get(id).get("userList");
            boolean hasUserId = hasUserId(userList, userId);
            if (hasUserId) {
                throw new Exception("중복 접속 발견 서버 차단");
//                System.out.println("중복접속 발견 유저등록 중단");
            }
//            else {
//            }
        }
        CLIENTS.put(id + "_" + userId + "_" + uniqueId, session);

        //user api post

        //header setting
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(new MediaType("application","json", Charset.forName("UTF-8")));


        //Object Mapper Json Binding
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        String params = objectMapper.writeValueAsString(map);

        HttpEntity entity = new HttpEntity(params, httpHeaders);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> userInfo = restTemplate.exchange(serverIp + "/api/user/userInfo", HttpMethod.POST, entity, Map.class);

        System.out.println("userInfo :" + userInfo);
        Map<String, Object> clientInsertUserInfo = new HashMap<>();
        //api로 가져온 userinfo 삽입
        List<Map<String, Object>> clientUserList = new ArrayList<>();

        Map<String, Object> currentUser = new HashMap<>();
        currentUser.put("userId", userId);
        currentUser.put("userChattingBanState", false);
        currentUser.put("userChattingLastBanTime", "");
        currentUser.put("uniqueId", uniqueId);



        if(ClientList.get(id) == null) {
            ClientList.put(id, clientInsertUserInfo);
            currentUser.put("userId", userId);
            currentUser.put("userChattingBanState", false);
            currentUser.put("userChattingLastBanTime", "");
            clientUserList.add(currentUser);
            ClientList.get(id).put("userList", clientUserList);
            ClientList.get(id).put("userCount", ((List<Map<String, Object>>) ClientList.get(id).get("userList")).size());
            ClientList.get(id).put("stageUrl", "");

        }else {
            List<Map<String, Object>> userList = (List<Map<String, Object>>) ClientList.get(id).get("userList");

            System.out.println("Add userList: " + userList);
            userList.add(currentUser);
            ClientList.get(id).put("userCount", userList.size());
        }
        System.out.println("ClientList: "+ ClientList);
        this.sendToBrodcastInformation(id);
    }

    private boolean hasUserId(List<Map<String, Object>> userList, String userId) {
        for(Map<String, Object> user : userList) {
            String existUserId = (String) user.get("userId");
            if (existUserId.equals(userId)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 연결 종 시
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {

        System.out.println("afterConnectionClosed");

        System.out.println("Client: " + CLIENTS);

        Map<String, Object> inputUserSession = new HashMap<>();
        String uniqueId = session.getId();
        System.out.println("uniqueId:" + uniqueId);

        String urlQuery = session.getUri().getQuery();
        String[] urlQuerySplit = urlQuery.split(",");

        for(String x : urlQuerySplit) {
            String[] inputMap = x.split("=");
            for(int i=0; i<x.length(); i++) {
                inputUserSession.put(inputMap[0], inputMap[1]);
            }
        }

        String id = (String) inputUserSession.get("roomId");  //메시지를 보낸 아이디 -> stageId로 변경예정
        String userId = (String) inputUserSession.get("userId");  //메시지를 보낸 아이디 -> stageId로 변경예정

        List<Map<String, Object>> userList = (List<Map<String, Object>>) ClientList.get(id).get("userList");

        for(int i=0; i<userList.size(); i++){
            if(userList.get(i).get("uniqueId").equals(uniqueId)){
                userList.remove(i);
                ClientList.get(id).put("userCount", userList.size());
            }
        }

        System.out.println("ClientList: " + ClientList);

        System.out.println("roomId:" + id +"의 " + userId + "사용자가 종료하였습니다."   );
        CLIENTS.remove(id + "_" + userId + "_" + uniqueId);
        this.sendToBrodcastInformation(id);
    }



    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<String, Object> inputUserSession = new HashMap<>();
        String playLoad = message.getPayload();
//    	playLoad.replaceAll("\"", "").replace("{", "").replace("}", "");
        Map<String, Object> messageMap = new HashMap<String, Object>();


//        System.out.println("message: " + message);

//         sessions.parallelStream().forEach(session -> session.sendMessage(message));

        String urlQuery = session.getUri().getQuery();
        String[] urlQuerySplit = urlQuery.split(",");

        for(String x : urlQuerySplit) {
            String[] inputMap = x.split("=");
            for(int i=0; i<x.length(); i++) {
                inputUserSession.put(inputMap[0], inputMap[1]);
            }
        }
        TextMessage objMessage = new TextMessage(objectMapper.writeValueAsString(inputUserSession));

        String id = (String) inputUserSession.get("roomId");  //메시지를 보낸 아이디 -> stageId로 변경예정
        String userId = (String) inputUserSession.get("userId");

//        System.out.println("objMessage:" + objMessage);
//        System.out.println("playLoad" + playLoad);
//   	룸아이디 비교하여 룸아이디가 같을 시 sendMessage(message);  

        System.out.println("CLIENTS: " + CLIENTS);
        String userMessage = message.getPayload();
        ObjectMapper mapper = new ObjectMapper();
        System.out.println("userMessage:" + userMessage);
        try{
            Map<String, Object> map = mapper.readValue(userMessage, Map.class);

            int messageType = (int) map.get("type");

            if(messageType > 0 && messageType < 7) {
                this.msgChk(message, map, id, userId, messageType);
            }else {
                throw new Exception("허용되지 않은 타입");
            }

            System.out.println("message :" + map.get("type"));
        } catch (IOException e){
            e.printStackTrace();
        }

    }

    private void sendMessageToUser(String roomId,String userId, TextMessage message) {
        String sendId = roomId + "_" + userId;
        WebSocketSession session = CLIENTS.get(sendId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /*
    send message
    * */

    public void msgChk(TextMessage message, Map<String, Object> map, String id, String userId, int messageType) {
        String userMessage = message.getPayload();
        ObjectMapper mapper = new ObjectMapper();

        System.out.println("message: " + message.getPayload());
        System.out.println("id " + id);

        System.out.println("message " + message);
        System.out.println("message pay " + message.getPayload().getBytes(StandardCharsets.UTF_8));

        CLIENTS.entrySet().forEach( arg->{
            String[] argSplit = arg.getKey().split("_");

            if(argSplit[0].equals(id)) {  //같은 아이디가 아니면 메시지를 전달합니다.
                Map<String, Object> messageMap = null;
                try {
                    messageMap = mapper.readValue(userMessage, Map.class);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
                //WebSocket Message Type에 따른
                if(messageType == 1){
                    //일반메세지
                    try {
                        Map<String, Object> chkChattingBanUserResult = this.chkChattingBanUser(id,userId);
                        System.out.println("chkChattingBanUserResult: " + chkChattingBanUserResult);
                        if((int) chkChattingBanUserResult.get("result") == 200) {
                            System.out.println("일반메세지 전송");
                            arg.getValue().sendMessage(message);
                        }else {
                            //채팅금지상태
                            Map<String, Object> result = new HashMap<>();

                            result.put("type", 0);

                            result.put("result", 200);
                            result.put("message", "현재 채팅금지상태입니다.");

                            String mapAsString = null;
                            if(argSplit[1].equals(userId)) {
                                mapAsString = new ObjectMapper().writeValueAsString(result);
                                TextMessage textMessage = new TextMessage(mapAsString);
                                this.sendMessageToUser(id, userId, textMessage);

                            }
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }else if( messageType == 2 ) {
                    //후원 : api
                    Map<String, Object> apiResult = this.stageDonation(id, userId, map);
                    Map<String, Object> result = new HashMap<>();
                    Map<String, Object> returnMessage = new HashMap<>();

                    result.put("type", 0);
                    result.put("result", apiResult.get("result"));
                    result.put("message", apiResult.get("message"));

                    returnMessage.put("type", 2);
                    returnMessage.put("message",(String) messageMap.get("message"));
                    returnMessage.put("donation", true);
                    returnMessage.put("donationImage", messageMap.get("donationImage"));
                    returnMessage.put("nickName", messageMap.get("nickName"));


                    String mapAsString = null;
                    String returnMapAsString = null;
                    Map<String, Object> chkChattingBanUserResult = this.chkChattingBanUser(id,userId);
                    if((int) chkChattingBanUserResult.get("result") == 200) {
                        try {
                            if (argSplit[1].equals(userId)) {
                                mapAsString = new ObjectMapper().writeValueAsString(result);
                                TextMessage textMessage = new TextMessage(mapAsString);
                                arg.getValue().sendMessage(textMessage);
                            }
                            returnMapAsString = new ObjectMapper().writeValueAsString(returnMessage);
                            TextMessage returnTextMessage = new TextMessage(returnMapAsString);
                            arg.getValue().sendMessage(returnTextMessage);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }else {
                        //채팅금지상태

                        result.put("type", 0);
                        result.put("result", 200);
                        result.put("message", "현재 채팅금지상태입니다.");

                        if(argSplit[1].equals(userId)) {
                            try {
                                mapAsString = new ObjectMapper().writeValueAsString(result);
                            } catch (JsonProcessingException e) {
                                e.printStackTrace();
                            }
                            TextMessage textMessage = new TextMessage(mapAsString);
                            this.sendMessageToUser(id, userId, textMessage);
                        }
                    }
                } else if( messageType == 3 ) {
                    //추방 : socket
                    String banUser = (String) messageMap.get("banUser");
                    try {
                        Map<String, Object> result = new HashMap<>();

                        result.put("type", 0);
                        result.put("result", 200);
                        result.put("message", "관리자의 의해 추방되었습니다.");

                        String mapAsString = null;
                        if(argSplit[1].equals(banUser)) {
                            mapAsString = new ObjectMapper().writeValueAsString(result);
                            TextMessage textMessage = new TextMessage(mapAsString);
                            this.sendMessageToUser(id, banUser, textMessage);
                            this.banUser(id, userId, banUser);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if( messageType == 4 ) {
                    //채팅금지 : socket
                    String banUser = (String) messageMap.get("chattingBanUser");
                    Map<String, Object> result = new HashMap<>();
                    result.put("type", 0);
                    result.put("result", 200);
                    result.put("message", "관리자의 채팅금지 상태입니다.");
                    String mapAsString = null;
                    try {
                        mapAsString = new ObjectMapper().writeValueAsString(result);
                    } catch (JsonProcessingException e) {
                        e.printStackTrace();
                    }
                    if(argSplit[1].equals(banUser)) {
                        TextMessage textMessage = new TextMessage(mapAsString);
                        this.sendMessageToUser(id, banUser, textMessage);
                        this.updateUserBanState(id, banUser);
                    }


                } else if( messageType == 5 ) {
                    //인터미션을 통한 url change
                } else if( messageType == 6) {
                    //방송관련정보

                }

            }
        });
    }

    /*도네이션*/
    public Map<String, Object> stageDonation(String roomId, String userId, Map<String, Object> map) {

        System.out.println("Donation Start");
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(new MediaType("application","json", Charset.forName("UTF-8")));
        Map<String, Object> paramsMap = new HashMap<>();

        String cometPurchaseUserId = (String) map.get("cometPurchaseUserId");
        String cometCurrentStreamKey = (String) map.get("cometCurrentStreamKey");
        String cometSalesStageId = (String) map.get("cometSalesStageId");
        int cometCount = (int) map.get("cometCount");

        paramsMap.put("cometPurchaseUserId",cometPurchaseUserId);
        paramsMap.put("cometCurrentStreamKey",cometCurrentStreamKey);
        paramsMap.put("cometSalesStageId", cometSalesStageId);
        paramsMap.put("cometCount", cometCount);
        String params = null;
        try {
            params = objectMapper.writeValueAsString(paramsMap);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        HttpEntity entity = new HttpEntity(params, httpHeaders);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> donationInsert = restTemplate.exchange(serverIp + "/api/donation/donationInsert", HttpMethod.POST, entity, Map.class);

        Map<String, Object> result = donationInsert.getBody();
        System.out.println("result:" + result);
        return result;
    }

    /*유저 추방*/
    public void userExile(String userId, String roomId) {
        System.out.println("유저 추방: " + userId + " , " + roomId);
        CLIENTS.remove(roomId + "_" + userId);
        System.out.println(userId+": 유저추방완료");
    }

    /*유저 채팅금지*/
    public void updateUserBanState(String roomId, String banUserId) {
        // 해당 roomId에 해당하는 TestRoom 객체를 가져옵니다.
        Map<String, Object> room = ClientList.get(roomId);
        List<Map<String, Object>> userList = (List<Map<String, Object>>) room.get("userList");
        String currentTime = this.currentTime();
        for (Map<String, Object> user : userList) {
            if (user.get("userId").equals(banUserId)) {
                user.put("userChattingBanState", true);
                user.put("userChattingLastBanTime", currentTime);
                break;
            }
        }
        System.out.println("ClientList: " + ClientList);
    }

    public void banUser(String roomId, String userId, String banUser) throws IOException {

        String sessionKey = roomId + "_" + banUser;

        if (CLIENTS.containsKey(sessionKey)) {
            WebSocketSession session = CLIENTS.get(sessionKey);
            session.close();
            CLIENTS.remove(sessionKey);

            List<Map<String, Object>> userList = (List<Map<String, Object>>) ClientList.get(roomId).get("userList");

            for (int i = 0; i < userList.size(); i++) {
                if (userList.get(i).get("userId").equals(banUser)) {
                    userList.remove(i);
                    ClientList.get(roomId).put("userCount", userList.size());
                }
            }

        }
    }

    public Map<String, Object> chkChattingBanUser(String roomId, String userId) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> room = ClientList.get(roomId);
        List<Map<String, Object>> userList = (List<Map<String, Object>>) room.get("userList");
        System.out.println("userList: "+ userList);
        String currentTime = this.currentTime();
        for (Map<String, Object> user : userList) {
            if (user.get("userId").equals(userId)) {
                System.out.println("유저확인 시간 확인:" + userId );
                if((boolean) user.get("userChattingBanState")) {

                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
                    System.out.println("currentTime: " + currentTime);
                    System.out.println("String) user.get(userChattingLastBanTime): " + (String) user.get("userChattingLastBanTime"));
                    LocalDateTime localDateTime1 = LocalDateTime.parse(currentTime, formatter);
                    LocalDateTime localDateTime2 = LocalDateTime.parse((String) user.get("userChattingLastBanTime"), formatter);

                    // 두 시간을 비교합니다.
                    if (localDateTime2.isBefore(localDateTime1)) {
                        System.out.println(localDateTime2 + " 이 " + localDateTime1 + " 보다 빠릅니다.");
                        Duration duration = Duration.between(localDateTime1, localDateTime2);
                        // 두 시간의 차이가 1분 이상인지 확인합니다.
                        long diffMinutes = duration.toMinutes();

                        if (Math.abs(diffMinutes) <= 1) {
                            System.out.println("두 시간의 차이가 1분 이하입니다. 채팅금지상태의 유저이므로 채팅을 금지합니다.");

                            result.put("result", 400);
                            result.put("message", "채팅금지");

                            return result;
                        }else {
                            System.out.println("두 시간의 차이가 1분 이상입니다. 채팅금지를 해제합니다");
                            user.put("userChattingBanState", false);
                            user.put("userChattingLastBanTime", "");
                        }
                    }
                }
            }
            System.out.println("result 반환:" + result);
            result.put("message", "success");
            result.put("result", 200);
        }

        return result;
    }

    /**
     * 방송정보 전달
     * */
    public void sendToBrodcastInformation(String roomId) {
        System.out.println("접속자 변동 정보 전달");
        System.out.println("Client Information: " + ClientList);
        Map<String, Object> result = new HashMap<>();
        int userCount = (Integer) ClientList.get(roomId).get("userCount");
        String stageUrl = (String) ClientList.get(roomId).get("stageUrl");
        result.put("type", 6);
        result.put("userCount", userCount);
        result.put("stageUrl", stageUrl);
        String mapAsString = null;
        try {
            mapAsString = new ObjectMapper().writeValueAsString(result);
            TextMessage textMessage = new TextMessage(mapAsString);
            CLIENTS.entrySet().forEach( arg->{
                String[] argSplit = arg.getKey().split("_");

                if(argSplit[0].equals(roomId)) {
                    try {
                        arg.getValue().sendMessage(textMessage);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 인터미션
     * */
    public void sendToModifyBroadcastUrl() {

    }


}