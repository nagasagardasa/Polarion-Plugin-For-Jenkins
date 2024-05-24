package io.jenkins.plugins.polarionPlugin;

import java.net.http.HttpResponse;

public class HttpException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public HttpException(HttpResponse<?> response, long time, String body) {

        super(String.format("<- %s %s (%sms)%n<- %s", response.statusCode(), "", time, body));
    }

    public HttpException(String message) {
        super(message);
    }
}
