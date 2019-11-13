package com.gityou.server.service;

public class AuthService {


    /*
     * 根据输入验证用户和密码
     * */
    public String auth(String pathInfo, String loginInfo) {
        String[] paths = pathInfo.split("/");
        String repoUser = paths[1];
        String repoName = paths[2];
        System.out.println("pathUser: " + repoUser);

        String username = loginInfo.split(":")[0];
        String password = loginInfo.split(":")[1];
        System.out.println("loginUser: " + username);
        System.out.println("password: " + password);

        if (repoUser.equals(username))
            return "xiaorui";
        else
            return "";
    }

}// end
