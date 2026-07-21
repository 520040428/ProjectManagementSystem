package com.laigeoffer.pmhub.gateway.config;

import com.google.code.kaptcha.text.impl.DefaultTextCreator;

import java.util.Random;

/**
 * 验证码文本生成器(数字验证码使用的自定义的验证码文本生成器)
 * 
 * @author JingYi
 */

//DefaultTestCreator时Kaptcha库中默认的文本生成器，作用是生成纯数字的验证码
public class KaptchaTextCreator extends DefaultTextCreator
{
    // 定义并初始化一个静态的，不可变的字符串数组，数组中包含了从"0"到"10"的数字字符串
    private static final String[] CNUMBERS = "0,1,2,3,4,5,6,7,8,9,10".split(",");

    @Override
    public String getText()
    {
        Integer result = 0;
        Random random = new Random();
        int x = random.nextInt(10);
        int y = random.nextInt(10);
        StringBuilder suChinese = new StringBuilder();
        int randomoperands = random.nextInt(3);

        // 规定我们的数字验证码的选择
        // 如果randomperands==0，则显示的是数字乘法
        if (randomoperands == 0)
        {
            result = x * y;
            suChinese.append(CNUMBERS[x]);
            suChinese.append("*");
            suChinese.append(CNUMBERS[y]);
        }
        // 如果等于1，如果除数不等于0，则是除法，否则就是加法
        else if (randomoperands == 1)
        {
            if ((x != 0) && y % x == 0)
            {
                result = y / x;
                suChinese.append(CNUMBERS[y]);
                suChinese.append("/");
                suChinese.append(CNUMBERS[x]);
            }
            else
            {
                result = x + y;
                suChinese.append(CNUMBERS[x]);
                suChinese.append("+");
                suChinese.append(CNUMBERS[y]);
            }
        }
        else if (randomoperands == 2)
        {
            if (x >= y)
            {
                result = x - y;
                suChinese.append(CNUMBERS[x]);
                suChinese.append("-");
                suChinese.append(CNUMBERS[y]);
            }
            else
            {
                result = y - x;
                suChinese.append(CNUMBERS[y]);
                suChinese.append("-");
                suChinese.append(CNUMBERS[x]);
            }
        }
        else
        {
            result = x + y;
            suChinese.append(CNUMBERS[x]);
            suChinese.append("+");
            suChinese.append(CNUMBERS[y]);
        }
        // 将我们生成的验证码和我们的答案同时发出去，前者验证用户，后者交给后端验证答案是否正确
        suChinese.append("=?@" + result);
        return suChinese.toString();
    }
}