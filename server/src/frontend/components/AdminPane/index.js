import {connect} from "react-redux";
import {componentsOperations} from "../duck";
import React, {Component} from "react";
import adminPane from "./presenter.jsx"

const mapStateToProps = (state) => {
    const {
        adminMode
    } = state.components

    return {
        adminMode
    }
}

const mapDispatchToProps = (dispatch) => {
    const toggleAdminMode = () => {
        dispatch(componentsOperations.toggleAdminMode())
    }
    return {
        toggleAdminMode
    }
}

function mergeProps(stateProps, dispatchProps, ownProps) {
    return {
        ...stateProps,
        ...dispatchProps,
        ...ownProps
    }
}

class AdminPane extends Component {
    render() {
        return adminPane(this.props)
    }
}

export default connect(mapStateToProps, mapDispatchToProps, mergeProps) (AdminPane)