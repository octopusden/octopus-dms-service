import React, {Component} from 'react'
import './style.css'
import {connect} from "react-redux";
import {componentsTree, treeLevel} from "./presenter.jsx";
import {componentsOperations} from "../duck";
import queryString from "query-string";
import history from "../../utils/history";

const mapStateToProps = (state) => {
  const {
    loggedUser, components, componentsVersions, loadingComponents, loadingArtifactsList, currentArtifacts,
    errorMessage, showRc, searchQueryValid, searchResult, searching, toggleAdminMode, adminMode, confirmation
  } = state.components
  return {
    loggedUser,
    components,
    componentsVersions,
    loadingComponents,
    loadingArtifactsList,
    currentArtifacts,
    errorMessage,
    showRc,
    searchQueryValid,
    searchResult,
    searching,
    toggleAdminMode,
    adminMode,
    confirmation
  }
}

const mapDispatchToProps = (dispatch) => {
  const toggleRc = () => {
    dispatch(componentsOperations.toggleRc())
  }
  const fetchComponents = () => {
    dispatch(componentsOperations.getComponents())
  }
  const expandComponent = (componentId) => {
    dispatch(componentsOperations.expandComponent(componentId))
  }
  const closeComponent = (componentId) => {
    dispatch(componentsOperations.closeComponent(componentId))
  }
  const fetchComponentMinorVersions = (componentId) => {
    dispatch(componentsOperations.getComponentMinorVersions(componentId))
  }
  const expandMinorVersion = (componentId, minorVersion) => {
    dispatch(componentsOperations.expandMinorVersion(componentId, minorVersion))
  }
  const closeMinorVersion = (componentId, minorVersion) => {
    dispatch(componentsOperations.closeMinorVersion(componentId, minorVersion))
  }
  const fetchComponentVersions = (componentId, minorVersion) => {
    dispatch(componentsOperations.getComponentVersions(componentId, minorVersion))
  }
  const selectVersion = (componentId, minorVersion, version) => {
    dispatch(componentsOperations.selectVersion(componentId, minorVersion, version))
  }

  return {
    toggleRc,
    fetchComponents,
    expandComponent,
    closeComponent,
    fetchComponentMinorVersions,
    expandMinorVersion,
    closeMinorVersion,
    fetchComponentVersions,
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
  const {selectedComponent, selectedVersion, selectedMinor} = props.currentArtifacts

  return {
    ...currentUrlProps,
    component: selectedComponent == null ? undefined : selectedComponent,
    version: selectedVersion == null ? undefined : selectedVersion,
    minor: selectedMinor == null ? undefined : selectedMinor,
  }
}

class ComponentsTree extends Component {

  componentDidUpdate (prev) {
    const urlState = propsToUrl(this.props)
    history.push({search: queryString.stringify(urlState)})
  }

  componentDidMount () {
    const urlProps = queryString.parse(history.location.search)
    console.debug('urlProps', urlProps)
    const {component, minor, version} = urlProps
    const {fetchComponents, fetchComponentMinorVersions, fetchComponentVersions, selectVersion} = this.props

    fetchComponents()
    if (component) {
      fetchComponentMinorVersions(component)
      if (minor) {
        fetchComponentVersions(component, minor)
        if (version) {
          selectVersion(component, minor, version)
        }
      }
    }
  }

  render () {
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
      default:
        this.handleVersionSelect(nodeData)
    }
  }

  handleClickOnRoot = (nodeData) => {
    const {components, fetchComponentMinorVersions, expandComponent, closeComponent} = this.props
    const {componentId, isExpanded} = nodeData

    if (isExpanded) {
      closeComponent(componentId)
      return
    }

    if (!components[componentId].minorVersions || components[componentId].loadingError) {
      fetchComponentMinorVersions(componentId)
    } else {
      expandComponent(componentId)
    }
  }

  handleMinorVersionSelect = (nodeData) => {
    const {components, fetchComponentVersions, expandMinorVersion, closeMinorVersion} = this.props
    const {componentId, version, isExpanded} = nodeData

    if (isExpanded) {
      closeMinorVersion(componentId, version)
      return
    }

    if (!components[componentId].minorVersions.versions || components[componentId].minorVersions.loadingError) {
      fetchComponentVersions(componentId, version)
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
}

export default connect(mapStateToProps, mapDispatchToProps, mergeProps) (ComponentsTree)
