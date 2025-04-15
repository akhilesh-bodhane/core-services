package org.egov.web.notification.mail.service;

import java.io.IOException;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.util.ByteArrayDataSource;

import org.egov.web.notification.mail.model.Attachment;
import org.egov.web.notification.mail.model.Email;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.Base64Utils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.sendgrid.Attachments;
import com.sendgrid.Content;
import com.sendgrid.Mail;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;

import lombok.extern.slf4j.Slf4j;

@Service
@ConditionalOnProperty(value = "mail.enabled", havingValue = "true")
@Slf4j
public class ExternalEmailService implements EmailService {

	public static final String EXCEPTION_MESSAGE = "Exception creating HTML email";
	/********* Attachment Related Enhancement ************/
	public static final String SMTP_GMAIL = "smtp.gmail.com";
	public static final String SMTP_SENDGRID = "smtp.sendgrid.com";

	private JavaMailSenderImpl mailSender;

	public ExternalEmailService(JavaMailSenderImpl mailSender) {
		this.mailSender = mailSender;
	}

	@Override
	public void sendEmail(Email email) {
		if (email.isHtml()) {
			sendHTMLEmail(email);
		} else {
			sendTextEmail(email);
		}
	}

	private void sendTextEmail(Email email) {
		/********* Attachment Related Enhancement ************/
		String host = mailSender.getHost();
		if (!StringUtils.isEmpty(host) && SMTP_SENDGRID.equals(host)) {
			com.sendgrid.Email from = new com.sendgrid.Email(mailSender.getUsername());
			com.sendgrid.Email to = new com.sendgrid.Email(email.getToAddress());
			Content content = new Content("text/html", email.getBody());
			Mail mail = new Mail(from, email.getSubject(), to, content);
			SendGrid sg = new SendGrid(mailSender.getPassword());
			Request request = new Request();
			try {
				request.setMethod(Method.POST);
				request.setEndpoint("mail/send");
				request.setBody(mail.build());
				@SuppressWarnings("unused")
				Response response = sg.api(request);
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}

		} else {
			final SimpleMailMessage mailMessage = new SimpleMailMessage();
			mailMessage.setTo(email.getToAddress());
			mailMessage.setSubject(email.getSubject());
			mailMessage.setText(email.getBody());
			mailSender.send(mailMessage);

		}

	}

	private void sendHTMLEmail(Email email) {
		String host = mailSender.getHost();
		/********* Attachment Related Enhancement ************/
		if (!StringUtils.isEmpty(host) && SMTP_SENDGRID.equals(host)) {
			com.sendgrid.Email from = new com.sendgrid.Email(mailSender.getUsername());
			com.sendgrid.Email to = new com.sendgrid.Email(email.getToAddress());
			Content content = new Content("text/html", email.getBody());
			Mail mail = new Mail(from, email.getSubject(), to, content);
			if (!CollectionUtils.isEmpty(email.getAttachments())) {
				for (Attachment eachAttachment : email.getAttachments()) {
					Attachments attachments = new Attachments();
					attachments.setContent(Base64Utils.encodeToString(eachAttachment.getFileContent()));
					attachments.setType(eachAttachment.getFileType());
					attachments.setFilename(eachAttachment.getFileName());
					mail.addAttachments(attachments);

				}
			}

			SendGrid sg = new SendGrid(mailSender.getPassword());
			Request request = new Request();
			try {
				request.setMethod(Method.POST);
				request.setEndpoint("mail/send");
				request.setBody(mail.build());
				@SuppressWarnings("unused")
				Response response = sg.api(request);
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}

		} else {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper;
			try {
				helper = new MimeMessageHelper(message, true);
				helper.setTo(email.getToAddress());
				helper.setSubject(email.getSubject());
				helper.setText(email.getBody(), true);
				if (!CollectionUtils.isEmpty(email.getAttachments())) {
					for (Attachment eachAttachment : email.getAttachments()) {
						ByteArrayDataSource source = new ByteArrayDataSource(eachAttachment.getFileContent(),
								eachAttachment.getFileType());
						String filename = eachAttachment.getFileName();
						if (filename != null) {
							helper.addAttachment(eachAttachment.getFileName(), source);
						} else {
						    log.warn("Skipping attachment because filename is null");
						}
						
					}
				}

			} catch (MessagingException e) {
				log.error(EXCEPTION_MESSAGE, e);
				throw new RuntimeException(e);
			}
			mailSender.send(message);

		}

	}
}
