import {Component} from 'react'
import './style.css'
import {connect} from "react-redux";
import {groupedComponentsTree, treeLevel} from "./presenter.jsx";
import {componentsOperations} from "../duck";
import queryString from "query-string";
import history from "../../utils/history";
import get from "lodash/get";

const mapStateToProps = (state) => {
    const {
        components, loadingComponents, loadingArtifactsList, currentArtifacts, errorMessage, confirmation, showRc
    } = state.components
    return {components, loadingComponents, loadingArtifactsList, currentArtifacts, errorMessage, confirmation, showRc}
}

const mapDispatchToProps = (dispatch) => {
    const expandComponentGroup = (groupId) => {
        dispatch(componentsOperations.expandComponent(groupId))
    }
    const closeComponentGroup = (groupId) => {
        dispatch(componentsOperations.closeComponent(groupId))
    }
    const expandGroupedComponent = (groupId, componentId) => {
        dispatch(componentsOperations.expandGroupedComponent(groupId, componentId))
    }
    const closeGroupedComponent = (groupId, componentId) => {
        dispatch(componentsOperations.closeGroupedComponent(groupId, componentId))
    }
    const getGroupedComponentMinorVersions = (groupId, componentId) => {
        dispatch(componentsOperations.getGroupedComponentMinorVersions(groupId, componentId))
    }
    const expandMinorVersion = (groupId, componentId, minorVersion) => {
        dispatch(componentsOperations.expandGroupedComponentMinorVersion(groupId, componentId, minorVersion))
    }
    const closeMinorVersion = (groupId, componentId, minorVersion) => {
        dispatch(componentsOperations.closeGroupedComponentMinorVersion(groupId, componentId, minorVersion))
    }
    const getVersions = (groupId, componentId, minorVersion) => {
        dispatch(componentsOperations.getGroupedComponentVersions(groupId, componentId, minorVersion))
    }
    const selectVersion = (groupId, componentId, minorVersion, version) => {
        dispatch(componentsOperations.selectGroupedComponentVersion(groupId, componentId, minorVersion, version))
    }

    return {
        expandComponentGroup,
        closeComponentGroup,
        expandGroupedComponent,
        closeGroupedComponent,
        getGroupedComponentMinorVersions,
        expandMinorVersion,
        closeMinorVersion,
        getVersions,
        selectVersion
    }
}

const mergeProps = (stateProps, dispatchProps, ownProps) => {
    return {
        ...stateProps,
        ...dispatchProps,
        ...ownProps
    }
}

const propsToUrl = (props) => {
    const currentUrlProps = queryString.parse(history.location.search)
    const {
        selectedGroup,
        selectedComponent,
        selectedMinor,
        selectedVersion
    } = props.currentArtifacts
    return {
        ...currentUrlProps,
        group: selectedGroup == null ? undefined : selectedGroup,
        component: selectedComponent == null ? undefined : selectedComponent,
        minor: selectedMinor == null ? undefined : selectedMinor,
        version: selectedVersion == null ? undefined : selectedVersion
    }
}

class GroupedComponentsTree extends Component {
    componentDidUpdate(prev) {
        const urlState = propsToUrl(this.props)
        history.push({search: queryString.stringify(urlState)})
    }

    componentDidMount() {
        const urlProps = queryString.parse(history.location.search)
        const {group, component, minor, version} = urlProps
        const {
            getGroupedComponents,
            expandComponentGroup,
            getGroupedComponentMinorVersions,
            getGroupedComponentVersions,
            selectVersion
        } = this.props

        getGroupedComponents()
        if (group) {
            expandComponentGroup(group)
            if (component) {
                getGroupedComponentMinorVersions(group, component)
                if (minor) {
                    getGroupedComponentVersions(group, component, minor)
                    if (version) {
                        selectVersion(group, component, minor, version)
                    }
                }
            }
        }
    }

    render() {
        return groupedComponentsTree({...this.props, handleNodeClick: this.handleNodeClick})
    }

    handleNodeClick = (nodeData, _nodePath, e) => {
        const {level} = nodeData
        switch (level) {
            case treeLevel.GROUP:
                this.handleClickOnComponentGroup(nodeData)
                break
            case treeLevel.COMPONENT:
                this.handleClickOnComponent(nodeData)
                break
            case treeLevel.MINOR:
                this.handleMinorVersionSelect(nodeData)
                break
            default:
                this.handleVersionSelect(nodeData)
        }
    }

    handleClickOnComponentGroup = (nodeData) => {
        const {expandComponentGroup, closeComponentGroup} = this.props
        const {groupId, isExpanded} = nodeData

        if (isExpanded) {
            closeComponentGroup(groupId)
        } else {
            expandComponentGroup(groupId)
        }
    }

    handleClickOnComponent = (nodeData) => {
        const {components, expandGroupedComponent, closeGroupedComponent, getGroupedComponentMinorVersions} = this.props
        const {groupId, componentId, isExpanded} = nodeData

        if (isExpanded) {
            closeGroupedComponent(groupId, componentId)
            return
        }

        const customComponent = get(components, [groupId, 'subComponents', componentId])
        if (!customComponent.minorVersions || customComponent.loadError) {
            getGroupedComponentMinorVersions(groupId, componentId)
        } else {
            expandGroupedComponent(groupId, componentId)
        }
    }

    handleMinorVersionSelect = (nodeData) => {
        const {components, expandMinorVersion, closeMinorVersion, getVersions} = this.props
        const {groupId, componentId, minor, isExpanded} = nodeData

        if (isExpanded) {
            closeMinorVersion(groupId, componentId, minor)
            return
        }

        const minorVersion = get(components, [groupId, 'subComponents', componentId, 'minorVersions', minor]);
        if (!minorVersion.versions || minorVersion.loadError) {
            getVersions(groupId, componentId, minor)
        } else {
            expandMinorVersion(groupId, componentId, minor)
        }
    }

    handleVersionSelect = (nodeData) => {
        const {selectVersion} = this.props
        const {groupId, componentId, minorVersion, version, isSelected} = nodeData
        if (!isSelected) {
            selectVersion(groupId, componentId, minorVersion, version)
        }
    }
}

export default connect(mapStateToProps, mapDispatchToProps, mergeProps)(GroupedComponentsTree)
