package com.esg.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** MAIL_ALWAYS_TO: outside production every message goes to one inbox, naming the real recipient in the subject. */
class EmailSenderTest {

  @SuppressWarnings("unchecked")
  private static SimpleMailMessage sent(String alwaysTo) {
    JavaMailSender mail = mock(JavaMailSender.class);
    ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(mail);
    new EmailSender(provider, "Atheris ESG <info@example.org>", alwaysTo).send("ada@client.example", "Report approved", "Body");
    ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mail).send(message.capture());
    return message.getValue();
  }

  @Test
  void redirectsEveryMessageWhenConfigured() {
    SimpleMailMessage m = sent("qa@example.org");
    assertThat(m.getTo()).containsExactly("qa@example.org");
    assertThat(m.getSubject()).isEqualTo("[for ada@client.example] Report approved");
    assertThat(m.getFrom()).isEqualTo("Atheris ESG <info@example.org>");
  }

  @Test
  void sendsToTheRecipientOtherwise() {
    SimpleMailMessage m = sent("");
    assertThat(m.getTo()).containsExactly("ada@client.example");
    assertThat(m.getSubject()).isEqualTo("Report approved");
  }
}
