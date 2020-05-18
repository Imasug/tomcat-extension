package jp.co.ctc_g.tomcat_extension.valves;

import java.io.CharArrayWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Calendar;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.syslog.SyslogDataFormat;
import org.apache.camel.component.syslog.SyslogFacility;
import org.apache.camel.component.syslog.SyslogMessage;
import org.apache.camel.component.syslog.SyslogSeverity;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.valves.AbstractAccessLogValve;

public class SyslogAccessLogValve extends AbstractAccessLogValve {

	private static final String DIRECT_URI = "direct:syslog";
	private static final String MINA_URI_FORMAT = "mina:udp://%s:%s?sync=false";

	private CamelContext ctx;
	private ProducerTemplate template;
	private String localHost;

	private String syslogHost = "localhost";
	private int port = 514;
	private SyslogFacility facility = SyslogFacility.LOCAL0;
	private SyslogSeverity severity = SyslogSeverity.INFO;

	public String getSyslogHost() {
		return syslogHost;
	}

	public void setSyslogHost(String syslogHost) {
		this.syslogHost = syslogHost;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public SyslogFacility getFacility() {
		return facility;
	}

	public void setFacility(String facility) {
		this.facility = SyslogFacility.valueOf(facility.toUpperCase());
	}

	public SyslogSeverity getSeverity() {
		return severity;
	}

	public void setSeverity(String severity) {
		this.severity = SyslogSeverity.valueOf(severity.toUpperCase());
	}

	@Override
	protected void log(CharArrayWriter message) {
		SyslogMessage syslogMessage = new SyslogMessage();
		syslogMessage.setTimestamp(Calendar.getInstance());
		syslogMessage.setHostname(localHost);
		syslogMessage.setFacility(facility);
		syslogMessage.setSeverity(severity);
		syslogMessage.setLogMessage(message.toString());
		template.asyncSendBody(DIRECT_URI, syslogMessage);
	}

	@Override
	protected synchronized void startInternal() throws LifecycleException {

		try {
			InetAddress address = InetAddress.getLocalHost();
			localHost = address.getHostName();
		} catch (UnknownHostException e1) {
			localHost = "UNKNOWN_LOCALHOST";
		}

		try {
			ctx = new DefaultCamelContext();
			SyslogDataFormat syslogDataFormat = new SyslogDataFormat();
			RouteBuilder builder = new RouteBuilder() {
				@Override
				public void configure() throws Exception {
					from(DIRECT_URI).marshal(syslogDataFormat).to(String.format(MINA_URI_FORMAT, syslogHost, port));
				}
			};
			ctx.addRoutes(builder);
			ctx.start();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		template = ctx.createProducerTemplate();

		super.startInternal();
	}

	@Override
	protected synchronized void stopInternal() throws LifecycleException {
		super.stopInternal();
		try {
			ctx.stop();
		} catch (Exception ignored) {
		}
	}
}
