import React from 'react'
import {Tab, Tabs} from "@blueprintjs/core";
import './style.css'
import ComponentsTree from "../ComponentsTree";
import SolutionTree from "../SolutionTree";

export default function componentGroupPane(props) {
    let {selectedComponentGroupTab, handleComponentGroupTabChange} = props

    return <div className="component-group-pane-wrapper">
        <Tabs id="left-column-tabs" onChange={handleComponentGroupTabChange} selectedTabId={selectedComponentGroupTab}
              large={true} renderActiveTabPanelOnly={true}>
            <Tab id="components" title="Components" panel={<ComponentsTree/>}/>
            <Tab id="solutions" title="Solutions" panel={<SolutionTree/>}/>
            {/*<Tab id="custom" title="Custom" panel={<p>customized</p>}/>*/}
            {/*<Tab id="clients" title="Clients" panel={<p>clients</p>}/>*/}
        </Tabs>
    </div>
}