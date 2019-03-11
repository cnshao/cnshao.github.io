package com.transactional.demo.demo;

import com.transactional.demo.mapper.IUserMapper;
import com.transactional.demo.entity.User;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest
public class DemoApplicationTests {
	@Autowired
	IUserMapper iUserMapper;

	public User findUserById(Long id) {
		return iUserMapper.findUserById(id);
	}

	public List<User> findAllUsers() {
		return iUserMapper.findAllUsers();
	}

	public int insertUser(User user) {
		return iUserMapper.insertUser(user);
	}

	@Test
	public void contextLoads() {
		User user = new User();
		user.setAge(123);
		user.setName("abc");

		System.out.println(iUserMapper.insertUser(user));
	}

}
