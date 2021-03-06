package com.silence.service

import com.silence.enties.User
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import com.silence.repository.UserMapper
import org.springframework.transaction.annotation.Transactional
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.CacheEvict

import java.util.List
import com.silence.util.UUIDUtil
import com.silence.util.SecurityUtil
import com.silence.domain.GroupList
import com.silence.domain.FriendList
import scala.collection.JavaConversions
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.silence.util.DateUtil
import com.silence.common.SystemConstant
import com.silence.util.WebUtil
import javax.servlet.http.HttpServletRequest
import com.silence.entity.Receive
import java.util.ArrayList
import com.silence.entity.ChatHistory

/**
 * @description 用户信息相关操作
 * @date 2017-04-06
 */
@Service
class UserService @Autowired()(private var userMapper: UserMapper) {

    private final val LOGGER: Logger = LoggerFactory.getLogger(classOf[UserService])
    
    //电子邮件相关服务
    @Autowired private var mailService: MailService = _

    def countUsers(key: String, Type: String): Int = {
        if (key == null || "".equals(key)) 
            userMapper.countUser(null)
        else
          userMapper.countUser(key)
    }
    
    /**
     * @description 查找好友群相关
     * @param key
     * @Type 查找类型
     */
    def findUsers(key: String, Type: String): List[User] = {
        if (key == null || "".equals(key))
            userMapper.findUsers(null)
        else
            userMapper.findUsers(key)
    }
    
    /**
     * @description 统计查询消息
     * @param uid 消息所属用户
     * @param mid 来自哪个用户
     * @param Type 消息类型，可能来自friend或者group
     */
    def countHistoryMessage(uid: Integer, mid: Integer, Type: String):Int = {
        Type match {
            case "friend" => userMapper.countHistoryMessage(uid, mid, Type)  
            case "group" => userMapper.countHistoryMessage(null, mid, Type)
        }
    }
    
    /**
     * @description 查询历史消息
     * @param uid
     * @param 
     */
    def findHistoryMessage(user: User, mid: Integer, Type: String):List[ChatHistory] = {
    		val list = new ArrayList[ChatHistory]()
        //单人聊天记录
        if ("friend".equals(Type)) {          
          	//查找聊天记录
          	val historys:List[Receive] = userMapper.findHistoryMessage(user.getId, mid, Type)
    		  	val toUser = findUserById(mid)
    			  JavaConversions.collectionAsScalaIterable(historys).foreach { history => {
    				    var chatHistory: ChatHistory = null
  						  if(history.getId == mid){
      							chatHistory = new ChatHistory(history.getId, toUser.getUsername,toUser.getAvatar,history.getContent,history.getTimestamp)
  	  					} else {
  		    					chatHistory = new ChatHistory(history.getId, user.getUsername,user.getAvatar,history.getContent,history.getTimestamp)
  				  		}
  			        list.add(chatHistory)
  			    } }
        }
        //群聊天记录
        if ("group".equals(Type)) {
            //查找聊天记录
          	val historys:List[Receive] = userMapper.findHistoryMessage(null, mid, Type)
            JavaConversions.collectionAsScalaIterable(historys).foreach { history => {
                var chatHistory: ChatHistory = null
                val u = findUserById(history.getFromid)
                if (history.getFromid().equals(user.getId)) {                  
                	  chatHistory = new ChatHistory(user.getId, user.getUsername,user.getAvatar,history.getContent,history.getTimestamp)
                } else {
                    chatHistory = new ChatHistory(history.getId, u.getUsername,u.getAvatar,history.getContent,history.getTimestamp)
                }
                list.add(chatHistory)
            }}
        }
        return list
    }

    /**
     * @description 查询离线消息
     * @param uid
     * @param status 历史消息还是离线消息 0代表离线 1表示已读
     */
    def findOffLineMessage(uid: Integer, status: Integer):List[Receive] = userMapper.findOffLineMessage(uid, status)
    
    
    /**
     * @description 保存用户聊天记录
     * @param receive 聊天记录信息
     * @return Int
     */
    def saveMessage(receive: Receive): Int = userMapper.saveMessage(receive)
    
    /**
     * @description 用户更新签名
     * @param user
     * @return Boolean
     */
    def updateSing(user: User): Boolean = {
        if (user == null || user.getSign == null || user.getId == null) {
            return false
        } else {
            return userMapper.updateSign(user.getSign, user.getId) == 1     
        }
    }
    
    /**
     * @description 激活码激活用户
     * @param activeCode
     * @return Int
     */
    def activeUser(activeCode: String): Int = {
        if (activeCode == null || "".equals(activeCode)) {
            return 0
        }
        userMapper.activeUser(activeCode)
    }
    
    /**
     * @description 用户邮件和密码是否匹配
     * @param user
     * @return User
     */
    def matchUser(user: User): User = {
        if (user == null || user.getEmail == null) {
            return null
        }
        val u: User = userMapper.matchUser(user.getEmail)
        //密码不匹配
        if(u == null || !SecurityUtil.matchs(user.getPassword, u.getPassword)){
            return null
        }
        u
    }
    
    /**
     * @description 根据群组ID查询群里用户的信息
     * @param gid
     * @return List[User]
     */
    @Cacheable(value = Array("findUserByGroupId"), keyGenerator = "wiselyKeyGenerator")
    def findUserByGroupId(gid: Int): List[User] = userMapper.findUserByGroupId(gid)
    
    /**
     * @description 根据ID查询用户好友分组列表信息
     * @param uid 用户ID
     * @return List[FriendList]
     */
    @Cacheable(value = Array("findFriendGroupsById"), keyGenerator = "wiselyKeyGenerator")
    def findFriendGroupsById(uid: Int): List[FriendList] = {
        var friends = userMapper.findFriendGroupsById(uid)
        //封装分组列表下的好友信息
        JavaConversions.collectionAsScalaIterable(friends).foreach { 
            friend:FriendList => {
                friend.list = userMapper.findUsersByFriendGroupIds(friend.getId)
            }
        }
        friends
    }
  
    /**
     * @description 根据ID查询用户信息
     * @param id
     * @return User
     */
    @Cacheable(value = Array("findUserById"), keyGenerator = "wiselyKeyGenerator")
    def findUserById(id: Integer): User = {
        if (id != null) userMapper.findUserById(id) else null
    }
    
    /**
     * @description 根据ID查询用户群组信息
     * @param id
     * @return List[Group]
     */
    @Cacheable(value = Array("findGroupsById"), keyGenerator = "wiselyKeyGenerator")
    def findGroupsById(id: Int): List[GroupList] = {
        userMapper.findGroupsById(id)
    }
    
    /**
     * @description 保存用户信息
     * @param user
     * @return Int
     */
    //清除缓存
    @CacheEvict(value = Array("findUserById","findFriendGroupsById","findUserByGroupId"), allEntries = true)  
    def saveUser(user: User, request: HttpServletRequest): Int = {
        if (user == null || user.getUsername == null || user.getPassword == null || user.getEmail == null) {
            return 0
        } else {          
            //激活码
            val activeCode = UUIDUtil.getUUID64String
        	  user.setActive(activeCode)
        	  user.setCreateDate(DateUtil.getDate)
        	  //加密密码
        	  user.setPassword(SecurityUtil.encrypt(user.getPassword))
        	  //发送激活电子邮件
        	  mailService.sendHtmlMail(user.getEmail, SystemConstant.SUBJECT, 
        	      user.getUsername +",请确定这是你本人注册的账号   " + ", " + WebUtil.getServerIpAdder(request) + "/user/active/" + activeCode)
        	  userMapper.saveUser(user)
        }
    }
        
}