package com.springms.cloud.controller;

import com.springms.cloud.entity.User;
import com.springms.cloud.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户微服务Controller。
 *
 * @author hmilyylimh
 *
 * @version 0.0.1
 *
 * @date 2017-10-19
 *
 */
@RestController
public class ProviderUserMysqlMybatisController {

    @Autowired
    private IUserService iUserService;

    @GetMapping("/simple/{id}")
    public User findUserById(@PathVariable Long id) {
        return this.iUserService.findUserById(id);
    }

    @GetMapping("/simple/list")
    public List<User> findUserList() {
        return this.iUserService.findAllUsers();
    }

    /**
     * 添加一个student,使用postMapping接收post请求
     *
     * http://localhost:8310/simple/addUser?username=user11&age=11&balance=11
     *
     * @return
     */
    @RequestMapping("/simple/addUser")
    public User addUser(@RequestParam(value = "username", required=false) String username, @RequestParam(value = "age", required=false) Integer age, @RequestParam(value = "balance", required=false) String balance){
        User user=new User();

        user.setUsername(username);
        user.setName(username);
        user.setAge(age);
        user.setBalance(balance);

        int result = iUserService.insertUser(user);
        iUserService.insertUser(new User("AAA", 110));
        iUserService.insertUser(new User("BBB", 120));
        iUserService.insertUser(new User("CCC", 130));
        iUserService.insertUser(new User("DDD", 140));
        iUserService.insertUser(new User("EEE", 150));
        iUserService.insertUser(new User("FFF", 160));
        iUserService.insertUser(new User("GGG", 170));
        User user1 = new User("HHHHHHH", 8000);
        user1.setId(1L);
        user1.setName("user1");
        iUserService.insertUser(user1);
        iUserService.insertUser(new User("III", 190));
        iUserService.insertUser(new User("JJJ", 1100));
        if(result > 0){
            return user;
        }

        user.setId(0L);
        user.setName(null);
        user.setUsername(null);
        user.setBalance(null);
        return user;
    }
}
