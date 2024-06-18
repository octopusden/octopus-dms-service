import React, {Component} from 'react'
import version from '../../../../version.json'
import footer from "./presenter.jsx";
import {connect} from "react-redux";
import get from "lodash/get"

const mapStateToProps = (state) => {
  const {loggedUser} = get(state, "components")
  return {
    loggedUser
  }
}

const mapDispatchToProps = (dispatch) => {
  return {}
}

const mergeProps = (stateProps, dispatchProps, ownProps) => {
  return {
    ...stateProps,
    ...dispatchProps,
    ...ownProps,
    version: version.version
  }
}

class Footer extends Component {

  render() {
    return footer(this.props)
  }
}
export default connect(mapStateToProps, mapDispatchToProps, mergeProps) (Footer)
