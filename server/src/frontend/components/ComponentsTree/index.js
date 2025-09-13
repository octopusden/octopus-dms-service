import {Component} from 'react'
import './style.css'
import {connect} from "react-redux";
import {componentsTree, treeLevel} from "./presenter.jsx";
import {componentsOperations} from "../duck";
import queryString from "query-string";
import history from "../../utils/history";
import get from "lodash/get";

const mapStateToProps = (state) => {
    const {
        components, loadingComponents, loadingArtifacts, currentArtifacts, errorMessage, confirmation, showRc, searching, searchQuery
    } = state.components
    return {components, loadingComponents, loadingArtifacts, currentArtifacts, errorMessage, confirmation, showRc, searching, searchQuery}
}

const mapDispatchToProps = (dispatch) => {
    const getComponents = (onSuccess) => {
        dispatch(componentsOperations.getComponents(null, onSuccess))
    }
    const expandComponent = (componentId) => {
        dispatch(componentsOperations.expandComponent(componentId))
    }
    const closeComponent = (componentId) => {
        dispatch(componentsOperations.closeComponent(componentId))
    }
    const getComponentMinorVersions = (componentId, onSuccess) => {
        dispatch(componentsOperations.getComponentMinorVersions(componentId, onSuccess))
    }
    const expandMinorVersion = (componentId, minorVersion) => {
        dispatch(componentsOperations.expandMinorVersion(componentId, minorVersion))
    }
    const closeMinorVersion = (componentId, minorVersion) => {
        dispatch(componentsOperations.closeMinorVersion(componentId, minorVersion))
    }
    const getComponentVersions = (componentId, minorVersion, onSuccess) => {
        dispatch(componentsOperations.getComponentVersions(componentId, minorVersion, onSuccess))
    }
    const selectVersion = (componentId, minorVersion, version) => {
        dispatch(componentsOperations.selectVersion(componentId, minorVersion, version))
    }

    const getDependencies = (componentId, minorVersion, version, onSuccess) => {
        dispatch(componentsOperations.getDependencies(componentId, minorVersion, version, onSuccess))
    }

    const selectDependency = (solutionId, solutionMinor, solutionVersion, componentId, version) => {
        dispatch(componentsOperations.selectDependency(solutionId, solutionMinor, solutionVersion, componentId, version))
    }

    return {
        getComponents,
        expandComponent,
        closeComponent,
        getComponentMinorVersions,
        expandMinorVersion,
        closeMinorVersion,
        getComponentVersions,
        selectVersion,
        getDependencies,
        selectDependency
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
    const {selectedComponent, selectedMinor, selectedVersion, } = props.currentArtifacts
    return {
        ...currentUrlProps,
        component: selectedComponent == null ? undefined : selectedComponent,
        minor: selectedMinor == null ? undefined : selectedMinor,
        version: selectedVersion == null ? undefined : selectedVersion,
    }
}

class ComponentsTree extends Component {
    componentDidUpdate(prev) {
        const urlState = propsToUrl(this.props)
        history.push({search: queryString.stringify(urlState)})
    }

    componentDidMount() {
        const urlProps = queryString.parse(history.location.search)
        const {component, minor, version} = urlProps
        const {getComponents, getComponentMinorVersions, getComponentVersions, selectVersion, getDependencies, selectDependency} = this.props

        getComponents(() => {
            if (component) {
                getComponentMinorVersions(component, () => {
                    if (minor) {
                        getComponentVersions(component, minor, () => {
                            if (version) {
                                if (component.solution) {
                                    getDependencies(component, minor, version, () => {
                                        // if (dependencyId && dependencyVersion) {
                                        //     selectDependency(component, minor, version, dependencyId, dependencyVersion)
                                        // }
                                    })
                                }
                                else {
                                    selectVersion(component, minor, version)
                                }
                            }
                        })
                    }
                })
            }
        })
    }

    render() {
        return componentsTree({...this.props, handleNodeClick: this.handleNodeClick})
    }

    handleNodeClick = (nodeData, _nodePath, e) => {
        const {level} = nodeData

        switch (level) {
            case treeLevel.ROOT:
                this.handleClickOnRoot(nodeData)
                break
            case treeLevel.MINOR:
                this.handleMinorVersionSelect(nodeData)
                break
            case treeLevel.VERSION:
                this.handleVersionSelect(nodeData)
                break
            default:
                this.handleDependencySelect(nodeData)
        }
    }

    handleClickOnRoot = (nodeData) => {
        const {components, getComponentMinorVersions, expandComponent, closeComponent} = this.props
        const {componentId, isExpanded} = nodeData

        if (isExpanded) {
            closeComponent(componentId)
            return
        }

        const component = components[componentId];
        if (!component.minorVersions || component.loadError) {
            getComponentMinorVersions(componentId)
        } else {
            expandComponent(componentId)
        }
    }

    handleMinorVersionSelect = (nodeData) => {
        const {components, getComponentVersions, expandMinorVersion, closeMinorVersion} = this.props
        const {componentId, version, isExpanded} = nodeData

        if (isExpanded) {
            closeMinorVersion(componentId, version)
            return
        }

        const minorVersion = get(components, [componentId, 'minorVersions', version])
        if (!minorVersion.versions || minorVersion.loadError) {
            getComponentVersions(componentId, version)
        } else {
            expandMinorVersion(componentId, version)
        }
    }

    handleVersionSelect = (nodeData) => {
        const {
            selectVersion
        } = this.props

        const {componentId, minorVersion, version, isSelected} = nodeData
        if (!isSelected) {
            selectVersion(componentId, minorVersion, version)
        }
    }

    handleDependencySelect = (nodeData) => {
        const {
            selectDependency
        } = this.props
        const {solutionId, solutionMinor, solutionVersion, componentId, version, isSelected} = nodeData
        if (!isSelected) {
            selectDependency(solutionId, solutionMinor, solutionVersion, componentId, version)
        }
    }
}

export default connect(mapStateToProps, mapDispatchToProps, mergeProps)(ComponentsTree)
