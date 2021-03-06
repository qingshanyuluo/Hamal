package com.github.bdqfork.rpc.protocol.client;

import com.github.bdqfork.core.Node;
import com.github.bdqfork.core.URL;
import com.github.bdqfork.core.constant.ProtocolProperty;
import com.github.bdqfork.core.extension.ExtensionLoader;
import com.github.bdqfork.rpc.Invoker;
import com.github.bdqfork.rpc.MethodInvocation;
import com.github.bdqfork.rpc.RpcContext;
import com.github.bdqfork.rpc.protocol.loadbalance.LoadBalancer;
import com.github.bdqfork.rpc.registry.Notifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author bdq
 * @since 2020/2/25
 */
public class ClusterInvoker<T> implements Invoker<T>, Notifier {
    private static final Logger log = LoggerFactory.getLogger(ClusterInvoker.class);
    private volatile boolean destroyed;
    /**
     * 消费者url
     */
    private URL url;
    /**
     * 负载均衡策略
     */
    private LoadBalancer loadBalancer;
    /**
     * 服务接口
     */
    private Class<T> serviceInterface;
    /**
     * 端点实例
     */
    private List<Endpoint<T>> endpoints;


    public ClusterInvoker(Class<T> serviceInterface, URL url) {
        this.serviceInterface = serviceInterface;
        this.url = url;
        initLoadBalancer(url);
        initEndpoints(url);
    }

    private void initEndpoints(URL url) {
        endpoints = new ArrayList<>();

        boolean direct = url.getParam(ProtocolProperty.DIRECT, false);

        if (direct) {
            String host = url.getParam(ProtocolProperty.HOST);
            Integer port = url.getParam(ProtocolProperty.PORT);
            URL providerUrl = new URL(ProtocolProperty.PROVIDER, host, port, url.getServiceName());
            providerUrl.addParams(url.getParams());

            if (log.isTraceEnabled()) {
                log.trace("try to create direct endpoint {}!", providerUrl.toPath());
            }

            endpoints.add(new RpcInvoker<>(serviceInterface, providerUrl));

            if (log.isDebugEnabled()) {
                log.debug("created direct endpoint {}!", providerUrl.toPath());
            }
        }
    }

    private void initLoadBalancer(URL url) {
        ExtensionLoader<LoadBalancer> extensionLoader = ExtensionLoader.getExtensionLoader(LoadBalancer.class);
        String loadBalanceType = url.getParam(ProtocolProperty.LOAD_BALANCER);
        this.loadBalancer = extensionLoader.getExtension(loadBalanceType);
    }

    @Override
    public Class<T> getInterface() {
        return serviceInterface;
    }

    @Override
    public Object invoke(MethodInvocation methodInvocation) throws Exception {
        RpcContext rpcContext = RpcContext.getContext();
        rpcContext.setConsumer(url);
        return loadBalancer.loadBalance(endpoints).invoke(methodInvocation);
    }

    @Override
    public URL getUrl() {
        return url;
    }

    @Override
    public boolean isAvailable() {
        if (destroyed) {
            return false;
        }
        for (Endpoint<?> endpoint : endpoints) {
            if (endpoint.isAvailable()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void destroy() {
        if (!destroyed) {
            destroyed = true;
            for (Endpoint<?> endpoint : endpoints) {
                endpoint.destroy();
            }
        }
    }

    @Override
    public void notify(List<URL> urls) {
        List<Endpoint<T>> copies = new ArrayList<>(endpoints.size());
        Collections.copy(copies, endpoints);

        endpoints = urls.stream()
                .map(url -> new RpcInvoker<>(serviceInterface, url))
                .collect(Collectors.toList());

        copies.forEach(Node::destroy);
    }
}
