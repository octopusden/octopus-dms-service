import React, {Component} from 'react'
import footer from "./presenter.jsx";
import {connect} from "react-redux";
import get from "lodash/get"

const mapStateToProps = (state) => {
  const {buildInfo, loggedUser} = get(state, "components")
  return {
    buildInfo, loggedUser
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
  }
}

class Footer extends Component {

  render() {
    return footer(this.props)
  }
}
export default connect(mapStateToProps, mapDispatchToProps, mergeProps) (Footer)
