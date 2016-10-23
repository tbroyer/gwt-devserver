# gwt-devserver

This project is a (prototype for a) Webpack-like devserver for GWT.

It can be used to either serve static files in a given directory
or reverse-proxy requests to another server,
and will intercept requests to the modules' `*.nocache.js` and other generated files to directly provide them through Super Dev Mode,
without the need to actually write those files to disk (and overwrite your production `*.nocache.js`).

The main class (`net.ltgt.gwt.devserver.DevServer`) takes similar arguments to `com.google.gwt.dev.codeserver.CodeServer`,
with the following additional ones:

 * `-proxyTo origin`: the `origin` (scheme, host, and optional port) to proxy requests to.
 * `-preserveHost`: when `-proxyTo` is used, whether to pass the `Host:` request as-is or rewrite it to the target origin (the default behavior.)
   This is similar to the `ProxyPreserveHost on` directive in Apache HTTPD's `mod_proxy`.
 * `-baseDir dir`: the directory whose files are directly served.
 * `-contextPath context`: the context path to be prefixed to the URLs to the `-baseDir`.
 * `-modulePathPrefix prefix`: the path where GWT modules are actually served (relative to the `-contextPath` if used.)
   This has the same meaning as the similarly named argument to `com.google.gwt.dev.DevMode`.

## Examples

In the command-lines below, `devserver` stands for `java -cp … net.ltgt.gwt.devserver.DevServer`.

### Standalone application

If you have a standalone application (either without server side, or relying on CORS),
you can serve its static files using something like:

```
devserver -baseDir src/main/webapp net.example.app.Application
```

You can then visit `http://localhost:8888/index.html` to load your application with Super Dev Mode.

In case the application wouldn't be deployed at the root of your web server and you want to test it in those conditions,
add the `-contextPath`:

```
devserver -baseDir src/main/webapp -contextPath myapp net.example.app.Application
```

The same `index.html` file in `src/main/webapp/` would then be served at `http://localhost:8888/myapp/index.html`.

### Application needing a server

If your application relies on server endpoints on the same origin,
you can reverse-proxy to the real server
(either on the same machine or a remote one; as you don't need direct access to the server's filesystem at all)
using something like:

```
devserver -proxyTo http://myserver:8080 net.example.app.Application
```

You can then visit `http://localhost:8888`
and the devserver will proxy that request to `http://myserver:8080`.
If the page includes the GWT module's `*.nocache.js` script,
the request will however be intercepted and a "compile on load" script will be served instead (the same that `CodeServer` generates into the `-launcherDir`),
so you'll run your application in Super Dev Mode without modifying your server.

If your GWT module dir is not at the root of the server (such as when your application is deployed into a non-empty context path),
you can use `-modulePathPrefix` to adjust it accordingly:

```
devserver -proxyTo http://myserver:8080 -modulePathPrefix webappcontext net.example.app.Application
```

In case your server adapts its behavior depending on the request's host,
such as redirects (for example, constructing a full URL for use with OAuth, OpenID Connect, CAS or similar),
you may want to use `-preserveHost` so that the request to the server goes with `Host: localhost:8888` instead of `Host: myserver:8080`:

```
devserver -proxyTo http://myserver:8080 -preserveHost net.example.app.Application
```

The way, your server's `ServletRequest#getServerName()` and `getServerPort()` (and `HttpServletRequest#getRequestURL()`) would reflect the host and port of the devserver,
so URLs constructed with those, to be passed to other servers to be eventually redirected to,
will lead to the devserver rather than the proxied server, without interrupting your development flow.
In non-servlet servers, those would be reflected in the `SERVER_NAME` and `SERVER_PORT` CGI variables, or the equivalent for your environment.

## Caveats

GWT-RPC won't work as-is, because the proxied server won't have the appropriate serialization policies.
If the proxied server runs on the same machine,
you can pass the `gwt.codeserver.port` system property with the same value as the devserver's `-codeServerPort` (defaults to `9876`)
so the RPC servlets load their serialization policies right from the `CodeServer` (that runs as part of the devserver.)

Currently, the devserver proxies everything in the GWT modules' paths to the `CodeServer`;
this will break GWT-RPC that use `@RemoteServiceRelativePath` without further customization.
This behavior might change in the future,
as most of those files will be referenced relative to [`GWT.getModuleBaseForStaticFiles()`](http://www.gwtproject.org/javadoc/latest/com/google/gwt/core/client/GWT.html#getModuleBaseForStaticFiles%28%29) anyway,
so they'll be loaded right from the `CodeServer` anyway;
but there are cases where resources from the GWT modules' _public path_ are referenced from non-GWT resources.
