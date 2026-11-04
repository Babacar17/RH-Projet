package com.soutenance.configuration;

import jakarta.faces.component.UIViewRoot;
import jakarta.faces.context.FacesContext;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;

import java.util.Map;

public class ViewScope implements Scope {

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        Map<String, Object> viewMap = getViewMap();
        if (viewMap == null) return objectFactory.getObject();
        if (!viewMap.containsKey(name)) {
            viewMap.put(name, objectFactory.getObject());
        }
        return viewMap.get(name);
    }

    @Override
    public Object remove(String name) {
        Map<String, Object> viewMap = getViewMap();
        return viewMap != null ? viewMap.remove(name) : null;
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {}

    @Override
    public Object resolveContextualObject(String key) { return null; }

    @Override
    public String getConversationId() {
        FacesContext ctx = FacesContext.getCurrentInstance();
        if (ctx == null) return null;
        UIViewRoot root = ctx.getViewRoot();
        return root != null ? root.getViewId() : null;
    }

    private Map<String, Object> getViewMap() {
        FacesContext ctx = FacesContext.getCurrentInstance();
        if (ctx == null) return null;
        UIViewRoot root = ctx.getViewRoot();
        return root != null ? root.getViewMap() : null;
    }
}