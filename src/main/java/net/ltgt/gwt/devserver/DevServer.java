package net.ltgt.gwt.devserver;

import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.dev.ArgProcessorBase;
import com.google.gwt.dev.cfg.ModuleDefLoader;
import com.google.gwt.dev.codeserver.CodeServer;
import com.google.gwt.dev.codeserver.Recompiler;
import com.google.gwt.dev.codeserver.WebServer;
import com.google.gwt.dev.jjs.JsOutputOption;
import com.google.gwt.dev.util.arg.*;
import com.google.gwt.dev.util.log.PrintWriterTreeLogger;
import com.google.gwt.thirdparty.guava.common.base.Charsets;
import com.google.gwt.thirdparty.guava.common.io.Resources;
import com.google.gwt.util.regexfilter.WhitelistRegexFilter;
import com.google.gwt.util.tools.ArgHandlerDir;
import com.google.gwt.util.tools.ArgHandlerFlag;
import com.google.gwt.util.tools.ArgHandlerString;
import javax.servlet.DispatcherType;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.eclipse.jetty.proxy.AsyncProxyServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.AsyncGzipFilter;

public class DevServer {

  static class ArgProcessor extends ArgProcessorBase {
    ArgProcessor(final Options options) {
      registerHandler(new ArgHandlerWorkDirOptional(new OptionWorkDir() {
        @Override
        public File getWorkDir() {
          return options.workDir;
        }

        @Override
        public void setWorkDir(File dir) {
          options.workDir = dir;
        }
      }));
      registerHandler(new ArgHandlerLogLevel(new OptionLogLevel() {
        @Override
        public TreeLogger.Type getLogLevel() {
          return options.logLevel;
        }

        @Override
        public void setLogLevel(TreeLogger.Type logLevel) {
          options.logLevel = logLevel;
        }
      }));
      final OptionGenerateJsInteropExports optionGenerateJsInteropExport = new OptionGenerateJsInteropExports() {
        @Override
        public boolean shouldGenerateJsInteropExports() {
          return options.generateExports;
        }

        @Override
        public void setGenerateJsInteropExports(boolean generateExports) {
          options.generateExports = generateExports;
        }

        @Override
        public WhitelistRegexFilter getJsInteropExportFilter() {
          return options.jsInteropExportFilter;
        }
      };
      registerHandler(new ArgHandlerGenerateJsInteropExports(optionGenerateJsInteropExport));
      registerHandler(new ArgHandlerFilterJsInteropExports(optionGenerateJsInteropExport));
      registerHandler(new ArgHandlerMethodNameDisplayMode(new OptionMethodNameDisplayMode() {
        @Override
        public Mode getMethodNameDisplayMode() {
          return options.methodNameDisplayMode;
        }

        @Override
        public void setMethodNameDisplayMode(Mode methodNameDisplayMode) {
          options.methodNameDisplayMode = methodNameDisplayMode;
        }
      }));
      registerHandler(new ArgHandlerScriptStyle(new OptionScriptStyle() {
        @Override
        public JsOutputOption getOutput() {
          return options.style;
        }

        @Override
        public void setOutput(JsOutputOption style) {
          options.style = style;
        }
      }));
      registerHandler(new ArgHandlerStrict(new OptionStrict() {
        @Override
        public boolean isStrict() {
          return options.failOnError;
        }

        @Override
        public void setStrict(boolean enabled) {
          options.failOnError = enabled;
        }
      }));
      registerHandler(new ArgHandlerBindAddress(new OptionBindAddress() {
        @Override
        public String getBindAddress() {
          return options.bindAddress;
        }

        @Override
        public String getConnectAddress() {
          return options.connectAddress;
        }

        @Override
        public void setBindAddress(String s) {
          options.bindAddress = s;
        }

        @Override
        public void setConnectAddress(String s) {
          options.connectAddress = s;
        }
      }));
      registerHandler(new ArgHandlerString() {

        @Override
        public String[] getDefaultArgs() {
          return new String[]{this.getTag(), "8888"};
        }

        @Override
        public String getPurpose() {
          return "Specifies the TCP port for the embedded web server (defaults to 8888)";
        }

        @Override
        public String getTag() {
          return "-port";
        }

        @Override
        public String[] getTagArgs() {
          return new String[]{"port-number | \"auto\""};
        }

        @Override
        public boolean setString(String value) {
          if(value.equals("auto")) {
            options.port = 0;
          } else {
            try {
              options.port = Integer.parseInt(value);
            } catch (NumberFormatException var3) {
              System.err.println("A port must be an integer or \"auto\"");
              return false;
            }
          }

          return true;
        }
      });
      registerHandler(new ArgHandlerString() {
        @Override
        public String[] getDefaultArgs() {
          return new String[]{"-codeServerPort", "9876"};
        }

        @Override
        public String getPurpose() {
          return "Specifies the TCP port for the code server (defaults to 9876)";
        }

        @Override
        public String getTag() {
          return "-codeServerPort";
        }

        @Override
        public String[] getTagArgs() {
          return new String[]{"port-number | \"auto\""};
        }

        @Override
        public boolean setString(String value) {
          if(value.equals("auto")) {
            options.codeServerPort = 0;
          } else {
            try {
              options.codeServerPort = Integer.parseInt(value);
            } catch (NumberFormatException var3) {
              System.err.println("A port must be an integer or \"auto\"");
              return false;
            }
          }

          return true;
        }
      });
      registerHandler(new ArgHandlerString() {
        @Override
        public boolean setString(String s) {
          // TODO: validate (http or https, no path, query or hash)
          options.proxyTo = s;
          return true;
        }

        @Override
        public String getPurpose() {
          return "Specifies an origin (scheme, host, and optional port) to proxy to.";
        }

        @Override
        public String getTag() {
          return "-proxyTo";
        }

        @Override
        public String[] getTagArgs() {
          return new String[] { "origin" };
        }
      });
      registerHandler(new ArgHandlerFlag() {
        @Override
        public boolean getDefaultValue() {
          return options.preserveHost;
        }

        @Override
        public String getPurposeSnippet() {
          return "Specifies whether the host request header is preserved or rewritten to the -proxyTo target origin";
        }

        @Override
        public boolean setFlag(boolean value) {
          options.preserveHost = value;
          return true;
        }
      });
      registerHandler(new ArgHandlerDir() {
        @Override
        public void setDir(File dir) {
          options.baseDir = dir;
        }

        @Override
        public String getPurpose() {
          return "Specifies the directory to serve (as static files only)";
        }

        @Override
        public String getTag() {
          return "-baseDir";
        }
      });
      registerHandler(new ArgHandlerString() {
        @Override
        public boolean setString(String s) {
          options.contextPath = s;
          return true;
        }

        @Override
        public String getPurpose() {
          return "Specifies the prefix to prepend to URLs when serving -baseDir.";
        }

        @Override
        public String getTag() {
          return "-contextPath";
        }

        @Override
        public String[] getTagArgs() {
          return new String[]{"path"};
        }
      });
      registerHandler(new ArgHandlerString() {
        @Override
        public boolean setString(String s) {
          if (s.startsWith("/")) {
            s = s.substring(1);
          }
          if (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
          }
          options.modulePathPrefix = s;
          return true;
        }

        @Override
        public String getPurpose() {
          return "The path inside -contextPath where modules are served.";
        }

        @Override
        public String getTag() {
          return "-modulePathPrefix";
        }

        @Override
        public String[] getTagArgs() {
          return new String[]{"prefix"};
        }
      });
      registerHandler(new ArgHandlerModuleName(new OptionModuleName() {
        @Override
        public List<String> getModuleNames() {
          return Collections.unmodifiableList(options.moduleNames);
        }

        @Override
        public void addModuleName(String moduleName) {
          options.moduleNames.add(moduleName);
        }

        @Override
        public void setModuleNames(List<String> moduleNames) {
          options.moduleNames.clear();
          options.moduleNames.addAll(moduleNames);
        }
      }));
    }

    @Override
    protected String getName() {
      return DevServer.class.getName();
    }
  }

  static class Options {
    File workDir;
    TreeLogger.Type logLevel;
    boolean generateExports;
    OptionMethodNameDisplayMode.Mode methodNameDisplayMode;
    JsOutputOption style;
    boolean failOnError;
    String bindAddress;
    String connectAddress;
    int port;
    int codeServerPort;
    String proxyTo;
    boolean preserveHost;
    File baseDir;
    String contextPath;
    String modulePathPrefix;
    final List<String> moduleNames = new ArrayList<>();
    final WhitelistRegexFilter jsInteropExportFilter = new WhitelistRegexFilter();
  }

  public static void main(String[] args) throws Exception {
    final Options options = new Options();
    if (!new ArgProcessor(options).processArgs(args)) {
      System.exit(1);
    }
    main(options);
  }

  static void main(Options options) throws Exception {
    if (options.moduleNames.isEmpty()) {
      System.err.println("At least one module must be supplied");
      System.exit(1);
    }
    if (options.baseDir != null && options.proxyTo != null) {
      System.err.println("-baseDir and -proxyTo are mutually exclusive");
      System.exit(1);
    } else if (options.baseDir == null && options.proxyTo == null) {
      System.err.println("One of -baseDir or -proxyTo must be specified");
      System.exit(1);
    }
    if (options.proxyTo == null && options.preserveHost) {
      System.err.println("-preserveHost is only meaningful along with -proxyTo; ignoring.");
    }
    if (options.baseDir == null && options.contextPath != null) {
      System.err.println("-contextPath is only meaningful along with -baseDir; ignoring.");
      options.contextPath = null;
    }
    WebServer codeServer = startCodeServer(options);
    options.codeServerPort = codeServer.getPort(); // account for "auto"
    start(options);
  }

  private static WebServer startCodeServer(Options options) throws Exception {
    ArrayList<String> args = new ArrayList<>();
    args.add("-noprecompile");
    if (options.codeServerPort >= 0) {
      args.add("-port");
      args.add(options.codeServerPort == 0 ? "auto" : String.valueOf(options.codeServerPort));
    }
    if (options.bindAddress != null) {
      args.add("-bindAddress");
      args.add(options.bindAddress);
    }
    if (options.workDir != null) {
      args.add("-workDir");
      args.add(options.workDir.toString());
    }
    if (options.logLevel != null) {
      args.add("-logLevel");
      args.add(options.logLevel.toString());
    }
    if (options.generateExports) {
      args.add("-generateJsInteropExports");
    }
    if (options.methodNameDisplayMode != null) {
      args.add("-XmethodNameDisplayMode");
      args.add(options.methodNameDisplayMode.toString());
    }
    if (options.style != null) {
      args.add("-style");
      args.add(options.style.toString());
    }
    if (options.failOnError) {
      args.add("-failOnError");
    }
    args.addAll(options.moduleNames);

    com.google.gwt.dev.codeserver.Options codeServerOptions = new com.google.gwt.dev.codeserver.Options();
    if (!codeServerOptions.parseArgs(args.toArray(new String[args.size()]))) {
      System.exit(1);
    }
    return CodeServer.start(codeServerOptions);
  }

  static Server start(final Options options) throws Exception {
    TreeLogger logger = new PrintWriterTreeLogger();

    Server server = new Server();
    ServerConnector connector = new ServerConnector(server);
    connector.setHost(options.bindAddress);
    connector.setPort(options.port);
    connector.setReuseAddress(false);
    connector.setSoLingerTime(0);
    server.addConnector(connector);

    ServletContextHandler handler = new ServletContextHandler(ServletContextHandler.NO_SECURITY | ServletContextHandler.NO_SESSIONS);
    handler.setContextPath(options.contextPath == null ? "/" : "/" + options.contextPath);

    URL url = Resources.getResource(Recompiler.class, "stub.nocache.js");
    final String template = Resources.toString(url, Charsets.UTF_8);
    for (String moduleName : options.moduleNames) {
      final String outputModuleName = ModuleDefLoader.loadFromClassPath(logger, moduleName).getName();
      final String script = template
          .replace("__MODULE_NAME__", outputModuleName)
          .replace("__SUPERDEV_PORT__", String.valueOf(options.codeServerPort));
      handler.addServlet(new ServletHolder(new HttpServlet() {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
          resp.setContentType("application/javascript; charset=UTF-8");
          resp.setHeader("Cache-Control", "no-cache");
          resp.getWriter().write(script);
        }
      }), "/" + (options.modulePathPrefix == null ? "" : options.modulePathPrefix + "/") + outputModuleName + "/" + outputModuleName + ".nocache.js");
      final ServletHolder proxyToCodeServer = new ServletHolder(new AsyncProxyServlet.Transparent());
      proxyToCodeServer.setInitParameter("proxyTo", "http://" + options.connectAddress + ":" + options.codeServerPort);
      if (options.modulePathPrefix != null) {
        proxyToCodeServer.setInitParameter("prefix", "/" + options.modulePathPrefix);
      }
      handler.addServlet(proxyToCodeServer, "/" + (options.modulePathPrefix == null ? "" : options.modulePathPrefix + "/") + outputModuleName + "/*");
    }

    if (options.proxyTo != null) {
      ServletHolder proxyTo = new ServletHolder(new AsyncProxyServlet.Transparent());
      proxyTo.setInitParameter("proxyTo", options.proxyTo);
      proxyTo.setInitParameter("preserveHost", Boolean.toString(options.preserveHost));
      handler.addServlet(proxyTo, "/*");
    } else if (options.baseDir != null) {
      handler.setResourceBase(options.baseDir.getAbsolutePath());
      handler.addServlet(new ServletHolder(new DefaultServlet()), "/*");
    }

    handler.addFilter(AsyncGzipFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
    server.setHandler(handler);
    try {
      server.start();
    } catch (Exception e) {
      logger.log(TreeLogger.ERROR, "cannot start web server", e);
      throw new UnableToCompleteException();
    }

    return server;
  }
}
