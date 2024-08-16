import React from 'react'
import {Tab, Tabs} from "@blueprintjs/core";
import './style.css'
import ComponentsTree from "../ComponentsTree";
import SolutionTree from "../SolutionTree";
import GroupedComponentsTree from "../GroupedComponentsTree";

export default function componentGroupPane(props) {
    const {selectedComponentGroupTab, handleComponentGroupTabChange, getCustomComponents, getClientsComponents} = props

    return <div className="component-group-pane-wrapper">
        <Tabs id="left-column-tabs" onChange={handleComponentGroupTabChange} selectedTabId={selectedComponentGroupTab}
              large={true} renderActiveTabPanelOnly={true}>
            <Tab id="components" icon="application" title="Components" panel={<ComponentsTree/>}/>
            <Tab id="solutions" icon="applications" title="Solutions" panel={<SolutionTree/>}/>
            <Tab id="custom" icon="fork" title="Custom" panel={<GroupedComponentsTree getGroupedComponents={getCustomComponents}/>}/>
            <Tab id="clients" icon="dollar" title="Clients" panel={<GroupedComponentsTree getGroupedComponents={getClientsComponents}/>}/>
        </Tabs>
    </div>
}