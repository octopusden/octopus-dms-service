import React from "react";
import './style.css'
import {Icon} from "@blueprintjs/core";

export default function meta(props) {
    const {meta} = props
    if (meta.ready) {
        return <div className="meta-wrapper">
            <div className="meta-column">
                <MetaItem icon='application' keyName='Component name' value={meta.componentName}/>
                <MetaItem icon='id-number' keyName='Component ID' value={meta.componentId}/>
                <MetaItem icon='box' keyName='Version' value={meta.version}/>
                <MetaItem icon='applications' keyName='Solution' value={meta.solution ? "yes" : "no"}/>
            </div>
            <div className="meta-column">
                <MetaItem icon='dollar' keyName='Client Code' value={!!meta.clientCode ? meta.clientCode : "none"}/>
                <MetaItem icon='fork' keyName='Parent Component ID' value={!!meta.parentComponent ? meta.parentComponent : "none"}/>
                <MetaItem icon='build' keyName='Status' value={meta.status}/>
                <MetaItem icon='git-push' keyName='Published' value={meta.published ? "yes" : "no"}/>
            </div>
        </div>
    } else {
        return <div className="meta-wrapper"></div>
    }
}

function MetaItem(props) {
    const {icon, keyName, value} = props
    return <div className="meta-item">
        <Icon className="meta-icon" icon={icon} iconSize={12}/>
        <strong className="meta-item-key">{keyName}:</strong>
        <div className="meta-item-value">{value}</div>
    </div>
}