import React from "react";
import './style.css'
import {Icon} from "@blueprintjs/core";

export default function meta(props) {
    const {selectedComponent, selectedComponentName, selectedVersion} = props
    return <div className="meta-wrapper">
        <div className="meta-column">
            <MetaItem icon='tag' keyName='Component name' value={selectedComponentName}/>
            <MetaItem icon='id-number' keyName='Component ID' value={selectedComponent}/>
            <MetaItem icon='box' keyName='Version' value={selectedVersion}/>
        </div>
    </div>
}

function MetaItem(props) {
    const {icon, keyName, value} = props
    return <div className="meta-item">
        <Icon className="meta-icon" icon={icon} iconSize={12}/>
        <strong className="meta-item-key">{keyName}:</strong>
        <div className="meta-item-value">{value}</div>
    </div>
}