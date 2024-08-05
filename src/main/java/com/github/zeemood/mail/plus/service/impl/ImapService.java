package com.github.zeemood.mail.plus.service.impl;

import com.github.zeemood.mail.plus.service.IMailService;
import com.github.zeemood.mail.plus.service.exception.MailPlusException;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import com.sun.mail.imap.IMAPStore;
import com.github.zeemood.mail.plus.domain.MailConn;
import com.github.zeemood.mail.plus.domain.MailConnCfg;
import com.github.zeemood.mail.plus.domain.MailItem;
import com.github.zeemood.mail.plus.domain.UniversalMail;
import com.github.zeemood.mail.plus.enums.ProxyTypeEnum;
import com.github.zeemood.mail.plus.utils.MailItemParser;

import javax.mail.*;
import javax.mail.search.FlagTerm;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;


/**
 * IMAP4协议邮箱收取类
 *
 * @author zeemoo
 * @date 2018/12/8
 */
public class ImapService implements IMailService {

    /**
     * Session properties的键名
     */
    private static final String PROPS_HOST = "mail.imap.host";
    private static final String PROPS_PORT = "mail.imap.port";
    private static final String PROPS_SSL = "mail.imap.ssl.enable";
    private static final String PROPS_AUTH = "mail.imap.auth";
    private static final String PROPS_SOCKS_PROXY_HOST = "mail.imap.socks.host";
    private static final String PROPS_SOCKS_PROXY_PORT = "mail.imap.socks.port";
    private static final String PROPS_HTTP_PROXY_HOST = "mail.imap.proxy.host";
    private static final String PROPS_HTTP_PROXY_PORT = "mail.imap.proxy.port";
    private static final String PROPS_HTTP_PROXY_USER = "mail.imap.proxy.user";
    private static final String PROPS_HTTP_PROXY_PASSWORD = "mail.imap.proxy.password";
    private static final String PROPS_PARTIALFETCH = "mail.imap.partialfetch";
    private static final String PROPS_STARTTLS = "mail.imap.starttls.enable";
    /**
     * 一次性最多同步的邮件数量
     */
    private static final int MAX_SYNCHRO_SIZE = 100;

    /**
     * 解析邮件
     *
     * @param mailItem      邮箱列表项
     * @param localSavePath 本地存储路径
     * @return
     * @throws MailPlusException
     */
    @Override
    public UniversalMail parseEmail(MailItem mailItem, String localSavePath) throws MailPlusException {
        return MailItemParser.parseMail(mailItem, localSavePath);
    }

    /**
     * 列举需要被同步的邮件
     *
     * @param mailConn  邮箱连接，可以做成这个类的字段
     * @param existUids 已同步的邮件uid
     * @return
     * @throws MailPlusException
     */
    @Override
    public Message[] listAll(MailConn mailConn, List<String> existUids, Integer MAX_NUMBER) throws MailPlusException {
        IMAPStore imapStore = mailConn.getImapStore();
        try {

            //只从收件箱中收取，所以必须白名单
            Folder defaultFolder = imapStore.getFolder("INBOX");
            //List<MailItem> mailItems = new ArrayList<>();
            Message[] messages = null;
            IMAPFolder imapFolder = (IMAPFolder) defaultFolder;
            //Gmail额外分层
            if (imapFolder.getName().equalsIgnoreCase("[gmail]")) {
                messages = listGmailMessageFolder(imapFolder);
            } else {
                messages = listFolderMessage(imapFolder);
            }

            return messages;
        } catch (MessagingException e) {
            e.printStackTrace();
            throw new MailPlusException(String.format("【IMAP服务】打开文件夹/获取邮件列表失败，错误信息【{}】"));
        }
    }

    /**
     * Gmail邮箱有额外的一层文件夹，需要被再打开一次
     *
     * @param target     存储需要被同步的邮件列表项
     * @param existUids  已同步下来的邮件uid
     * @param imapFolder 有邮件的文件夹
     * @return
     * @throws MessagingException
     */
    private Message[] listGmailMessageFolder(IMAPFolder imapFolder) throws MessagingException {
        Folder[] list = imapFolder.list();
        List<Message> messages = new ArrayList<>();
        for (Folder folder : list) {
            Message[] messages1 = listFolderMessage((IMAPFolder) folder);
            //把数组转为list
            List<Message> messages2 = Arrays.asList(messages1);
            messages.addAll(messages2);
        }
        return messages.toArray(new Message[messages.size()]);
    }

    /**
     * 通用的获取文件夹下邮件代码
     *
     * @param target     存储需要被同步的邮件列表项
     * @param existUids  已同步下来的邮件uid
     * @param imapFolder 有邮件的文件夹
     * @return
     * @throws MessagingException
     */
    private Message[] listFolderMessage(IMAPFolder imapFolder) throws MessagingException {

        imapFolder.open(Folder.READ_WRITE);
        //Message[] messages = imapFolder.getMessages();
        //读取只未读取的邮件
        Message[] messages = imapFolder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));

        return messages;
    }

    /**
     * 连接服务器
     *
     * @param mailConnCfg 连接配置
     * @param proxy       是否代理
     * @return 返回连接
     */
    @Override
    public MailConn createConn(MailConnCfg mailConnCfg, boolean proxy) throws MailPlusException {
        //构建Session Properties
        Properties properties = new Properties();
        properties.put(PROPS_HOST, mailConnCfg.getHost());
        properties.put(PROPS_PORT, mailConnCfg.getPort());
        properties.put(PROPS_SSL, mailConnCfg.isSsl());
        properties.put(PROPS_PARTIALFETCH, false);
        properties.put(PROPS_STARTTLS, false);
        properties.put(PROPS_AUTH, true);
        //设置代理
        if (proxy && mailConnCfg.getProxyType() != null) {
            ProxyTypeEnum proxyType = mailConnCfg.getProxyType();
            if (proxyType.equals(ProxyTypeEnum.SOCKS)) {
                properties.put(PROPS_SOCKS_PROXY_HOST, mailConnCfg.getSocksProxyHost());
                properties.put(PROPS_SOCKS_PROXY_PORT, mailConnCfg.getSocksProxyPort());
            } else if (proxyType.equals(ProxyTypeEnum.HTTP)) {
                properties.put(PROPS_HTTP_PROXY_HOST, mailConnCfg.getProxyHost());
                properties.put(PROPS_HTTP_PROXY_PORT, mailConnCfg.getProxyPort());
                properties.put(PROPS_HTTP_PROXY_USER, mailConnCfg.getProxyUsername());
                properties.put(PROPS_HTTP_PROXY_PASSWORD, mailConnCfg.getProxyPassword());
            }
        }
        //构建session
        Session session = Session.getInstance(properties);
        try {
            //连接
            Store store = session.getStore("imap");
            store.connect(mailConnCfg.getEmail(), mailConnCfg.getPassword());
            return MailConn.builder().imapStore((IMAPStore) store).build();
        } catch (NoSuchProviderException e) {
            e.printStackTrace();
            throw new MailPlusException(e.getMessage());
        } catch (MessagingException e) {
            e.printStackTrace();
            throw new MailPlusException(e.getMessage());
        }
    }
}
