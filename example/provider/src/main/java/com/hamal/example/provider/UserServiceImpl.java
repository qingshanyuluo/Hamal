package com.hamal.example.provider;

import cn.hamal.rpc.annotation.Service;
import com.hamal.example.api.User;
import com.hamal.example.api.UserService;

/**
 * @author bdq
 * @since 2020/2/26
 */
@Service(serviceInterface = UserService.class)
public class UserServiceImpl implements UserService {
    @Override
    public User getUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername("testRpc");
        user.setPassword("testpass");
        return user;
    }
}
