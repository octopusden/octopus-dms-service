import React from 'react'
import {Tab, Tabs} from "@blueprintjs/core";
import './style.css'
import ComponentsTree from "../ComponentsTree";

export default function componentGroupPane(props) {
    let {selectedComponentGroupTab, handleComponentGroupTabChange} = props

    return <div className="component-group-pane-wrapper">
        <Tabs id="TabsExample" onChange={handleComponentGroupTabChange} selectedTabId={selectedComponentGroupTab} large={true}>
            <Tab id="components" title="Components" panel={<ComponentsTree/>}/>
            <Tab id="solutions" title="Solutions" panel={<ComponentsTree/>}/>
            <Tab id="clients" title="Clients" panel={<ComponentsTree/>}/>
        </Tabs>
    </div>
}