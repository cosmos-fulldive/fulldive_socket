package com.fulldive.chatting.handler;

import java.io.IOException;
import java.nio.charset.Charset;
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

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class WebSocketHandler extends TextWebSocketHandler{

    private static final ConcurrentHashMap<String, WebSocketSession> CLIENTS = new ConcurrentHashMap<String, WebSocketSession>();
    private Map<WebSocketSession, String> sessionList = new ConcurrentHashMap<WebSocketSession, String>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String serverIp = "http://118.63.182.3:8880";
    private final Map<String, Map<String ,Object>> ClientList = new HashMap<>();

    private static int i;
    /**
     * 연결 성공 시
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        Map<String, Object> inputUserSession = new HashMap<>();
        
        String urlQuery = session.getUri().getQuery();
        String[] urlQuerySplit = urlQuery.split(",");

        System.out.println("urlQuery: " + urlQuery );
        System.out.println("urlQuerySplit: " + urlQuerySplit.length );

        for(String x : urlQuerySplit) {
     	   String[] inputMap = x.split("=");
     	   for(int i=0; i<x.length(); i++) {
     		   inputUserSession.put(inputMap[0], inputMap[1]);
     	   }
        }

        String id = (String) inputUserSession.get("roomId");  //메시지를 보낸 아이디 -> stageId로 변경예정
        String userId = (String) inputUserSession.get("userId");  //메시지를 보낸 아이디 -> stageId로 변경예정
        CLIENTS.put(id + "_" + userId, session);
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

        if(ClientList.get(id) == null) {
            ClientList.put(id, clientInsertUserInfo);
            currentUser.put("userId", userId);
            clientUserList.add(currentUser);
            ClientList.get(id).put("userList", clientUserList);
            ClientList.get(id).put("userCount", ((List<Map<String, Object>>) ClientList.get(id).get("userList")).size());
            ClientList.get(id).put("stageUrl", "");

        }else {
            List<Map<String, Object>> userList = (List<Map<String, Object>>) ClientList.get(id).get("userList");
            userList.add(currentUser);
            ClientList.get(id).put("userCount", userList.size());
        }
        System.out.println("ClientList: "+ ClientList);
    }

    /**
     * 연결 종 시
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    	System.out.println("afterConnectionClosed");
    	Map<String, Object> inputUserSession = new HashMap<>(); 
    	 
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
            if(userList.get(i).get("userId").equals(userId)){
                userList.remove(i);
                ClientList.get(id).put("userCount", userList.size());
            }
        }

        System.out.println("ClientList: " + ClientList);

        System.out.println("roomId:" + id +"의 " + userId + "사용자가 종료하였습니다."   );
    	CLIENTS.remove(id + "_" + userId);
    }
    
    

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    	Map<String, Object> inputUserSession = new HashMap<>(); 
    	String playLoad = message.getPayload();
//    	playLoad.replaceAll("\"", "").replace("{", "").replace("}", "");
    	Map<String, Object> messageMap = new HashMap<String, Object>(); 
    	messageMap.put("name", 1);
    	messageMap.put("name1", 2);
    	messageMap.put("name2", 3);
    	messageMap.put("name3", 4);
    	messageMap.put("name4", 5);

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
        try{
            Map<String, Object> map = mapper.readValue(userMessage, Map.class);

            int messageType = (int) map.get("type");

            if(messageType > 0 && messageType < 6) {
                this.msgChk(message, map, id, userId, messageType);
            }else {
                throw new Exception("허용되지 않은 타입");
            }

            System.out.println("message :" + map.get("type"));
        } catch (IOException e){
            e.printStackTrace();
        }

    }

    public void msgChk(TextMessage message, Map<String, Object> map, String id, String userId, int messageType) {
        System.out.println("message: " + message.getPayload());
        CLIENTS.entrySet().forEach( arg->{
            String[] argSplit = arg.getKey().split("_");

            if(argSplit[0].equals(id)) {  //같은 아이디가 아니면 메시지를 전달합니다.

                //WebSocket Message Type에 따른
                if(messageType == 1){
                    //일반메세지
                    try {
                        arg.getValue().sendMessage(message);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }else if( messageType == 2 ) {
                    //후원 : api
                    this.stageDonation(id, userId, map);
                }

                //인터미션을 통한 url change

                //신고기능 : api

                //추방 : socket

                //채팅금지 : socket


            }
        });
    }

    /*도네이션*/
    public Map<String, Object> stageDonation(String roomId, String userId, Map<String, Object> map) {

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(new MediaType("application","json", Charset.forName("UTF-8")));
        Map<String, Object> paramsMap = new HashMap<>();

        String cometCurrentStreamKey = (String) map.get("cometCurrentStreamKey");
        String cometSalesStageId = (String) map.get("cometSalesStageId");
        int cometCount = (int) map.get("cometCount");

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
        System.out.println("donationInsert: " + donationInsert);

        return null;
    }

    /*유저 추방*/
    public void userExile(String userId, String roomId) {
        System.out.println("유저 추방: " + userId + " , " + roomId);
        CLIENTS.remove(roomId + "_" + userId);
        System.out.println(userId+": 유저추방완료");
    }

    /*유저 채팅금지*/
    public void userNoChatting(String userId, String roomId) {

    }

}